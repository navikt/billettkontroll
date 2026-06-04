package no.nav.billettkontroll

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import jakarta.xml.ws.Endpoint
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

private const val SOAP_CONTENT_TYPE = "text/xml; charset=utf-8"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationIntegrationTest {

    private lateinit var azureAdWireMock: WireMockServer
    private lateinit var skjermedeWireMock: WireMockServer
    private lateinit var httpServer: com.sun.net.httpserver.HttpServer
    private lateinit var endpoint: Endpoint

    private var serverPort: Int = 0
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @BeforeAll
    fun startApplication() {
        azureAdWireMock = WireMockServer(wireMockConfig().dynamicPort())
        azureAdWireMock.start()

        skjermedeWireMock = WireMockServer(wireMockConfig().dynamicPort())
        skjermedeWireMock.start()

        azureAdWireMock.stubFor(
            post(urlEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token": "test-token", "expires_in": 3600}""")
                )
        )

        val tokenClient = AzureAdTokenClient(
            tokenEndpoint = "http://localhost:${azureAdWireMock.port()}/token",
            clientId = "test-client-id",
            clientSecret = "test-client-secret"
        )
        val skjermedePersonerClient = SkjermedePersonerClient(
            baseUrl = "http://localhost:${skjermedeWireMock.port()}",
            scope = "api://test/.default",
            tokenClient = tokenClient
        )
        val service = PipEgenAnsattServiceImpl(skjermedePersonerClient)
        val securityHandler = SecurityHeaderHandler(allowedUsernames = setOf("test-system"))

        val result = startServer(port = 0, service = service, securityHandler = securityHandler)
        httpServer = result.first
        endpoint = result.second
        serverPort = httpServer.address.port
    }

    @AfterAll
    fun stopApplication() {
        endpoint.stop()
        httpServer.stop(0)
        azureAdWireMock.stop()
        skjermedeWireMock.stop()
    }

    @BeforeEach
    fun resetSkjermedeStubs() {
        skjermedeWireMock.resetAll()
    }

    @Test
    fun `helse-endepunkt svarer 200 med UP-status`() {
        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$serverPort/internal/health/liveness"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("UP"))
    }

    @Test
    fun `SOAP-kall returnerer true for skjermet person`() {
        skjermedeWireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(aResponse().withStatus(200).withBody("true"))
        )

        val response = sendSoapRequest("12345678901")

        assertEquals(200, response.statusCode())
        assertTrue(parseEgenAnsatt(response.body()), "Forventet egenAnsatt=true for skjermet person")
    }

    @Test
    fun `SOAP-kall returnerer false for ikke-skjermet person`() {
        skjermedeWireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(aResponse().withStatus(200).withBody("false"))
        )

        val response = sendSoapRequest("12345678901")

        assertEquals(200, response.statusCode())
        assertFalse(parseEgenAnsatt(response.body()), "Forventet egenAnsatt=false for ikke-skjermet person")
    }

    @Test
    fun `SOAP-kall returnerer fault når skjermede-personer-tjenesten feiler`() {
        skjermedeWireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )

        val response = sendSoapRequest("12345678901")

        assertEquals(500, response.statusCode())
        assertTrue(
            response.body().contains("Fault", ignoreCase = true),
            "Forventet SOAP Fault ved downstream-feil, fikk: ${response.body()}"
        )
    }

    @Test
    fun `SOAP-kall uten WS-Security-header avvises med fault og kaller ikke downstream`() {
        val soapEnvelope = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:tns="http://nav.no/tjeneste/pip/pipEgenAnsatt/v1/">
              <soapenv:Body>
                <tns:erEgenAnsattEllerIFamilieMedEgenAnsatt>
                  <request>
                    <ident>12345678901</ident>
                  </request>
                </tns:erEgenAnsattEllerIFamilieMedEgenAnsatt>
              </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        val response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$serverPort/services/pipEgenAnsatt"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", SOAP_CONTENT_TYPE)
                .header("SOAPAction", "")
                .POST(HttpRequest.BodyPublishers.ofString(soapEnvelope))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(500, response.statusCode())
        assertTrue(
            response.body().contains("Fault", ignoreCase = true),
            "Forventet SOAP Fault ved manglende WS-Security-header, fikk: ${response.body()}"
        )
        skjermedeWireMock.verify(0, postRequestedFor(urlEqualTo("/skjermet")))
    }

    @Test
    fun `SOAP-kall videresender ident uendret til skjermede-personer`() {
        val testIdent = "98765432109"
        skjermedeWireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(aResponse().withStatus(200).withBody("false"))
        )

        sendSoapRequest(testIdent)

        skjermedeWireMock.verify(
            postRequestedFor(urlEqualTo("/skjermet"))
                .withRequestBody(equalToJson("""{"personident": "$testIdent"}"""))
        )
    }

    private fun sendSoapRequest(ident: String): HttpResponse<String> {
        val soapEnvelope = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:tns="http://nav.no/tjeneste/pip/pipEgenAnsatt/v1/"
                              xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
              <soapenv:Header>
                <wsse:Security>
                  <wsse:UsernameToken>
                    <wsse:Username>test-system</wsse:Username>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <tns:erEgenAnsattEllerIFamilieMedEgenAnsatt>
                  <request>
                    <ident>$ident</ident>
                  </request>
                </tns:erEgenAnsattEllerIFamilieMedEgenAnsatt>
              </soapenv:Body>
            </soapenv:Envelope>
        """.trimIndent()

        return httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$serverPort/services/pipEgenAnsatt"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", SOAP_CONTENT_TYPE)
                .header("SOAPAction", "")
                .POST(HttpRequest.BodyPublishers.ofString(soapEnvelope))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun parseEgenAnsatt(soapResponse: String): Boolean {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(soapResponse.byteInputStream())

        val result = XPathFactory.newInstance().newXPath()
            .evaluate("//*[local-name()='egenAnsatt']", doc)
            .trim()
        check(result.isNotEmpty()) { "Fant ikke <egenAnsatt> i SOAP-respons: $soapResponse" }
        return result.toBoolean()
    }
}
