package no.nav.billettkontroll

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class AzureAdTokenClientTest {

    private lateinit var wireMock: WireMockServer

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `should fetch token successfully`() {
        val tokenClient = AzureAdTokenClient(
            tokenEndpoint = "http://localhost:${wireMock.port()}/token",
            clientId = "test-client-id",
            clientSecret = "test-client-secret"
        )

        wireMock.stubFor(
            post(urlEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token": "test-token", "expires_in": 3600}""")
                )
        )

        val token = tokenClient.getToken("api://test/.default")

        assertEquals("test-token", token)
        wireMock.verify(postRequestedFor(urlEqualTo("/token"))
            .withRequestBody(containing("grant_type=client_credentials"))
            .withRequestBody(containing("client_id=test-client-id"))
            .withRequestBody(containing("scope=api%3A%2F%2Ftest%2F.default"))
        )
    }

    @Test
    fun `should cache token`() {
        val tokenClient = AzureAdTokenClient(
            tokenEndpoint = "http://localhost:${wireMock.port()}/token",
            clientId = "test-client-id",
            clientSecret = "test-client-secret"
        )

        wireMock.stubFor(
            post(urlEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token": "cached-token", "expires_in": 3600}""")
                )
        )

        val token1 = tokenClient.getToken("api://test/.default")
        val token2 = tokenClient.getToken("api://test/.default")

        assertEquals(token1, token2)
        wireMock.verify(1, postRequestedFor(urlEqualTo("/token")))
    }

    @Test
    fun `should throw exception on error response`() {
        val tokenClient = AzureAdTokenClient(
            tokenEndpoint = "http://localhost:${wireMock.port()}/token",
            clientId = "test-client-id",
            clientSecret = "test-client-secret"
        )

        wireMock.stubFor(
            post(urlEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")
                )
        )

        assertThrows<RuntimeException> {
            tokenClient.getToken("api://test/.default")
        }
    }
}
