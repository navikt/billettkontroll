package no.nav.billettkontroll

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkjermedePersonerClientTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var tokenClient: AzureAdTokenClient
    private lateinit var client: SkjermedePersonerClient

    @BeforeAll
    fun setup() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()

        tokenClient = mockk()
        every { tokenClient.getToken(any()) } returns "test-token"

        client = SkjermedePersonerClient(
            baseUrl = "http://localhost:${wireMock.port()}",
            scope = "api://test/.default",
            tokenClient = tokenClient
        )
    }

    @AfterAll
    fun teardown() {
        wireMock.stop()
    }

    @BeforeEach
    fun reset() {
        wireMock.resetAll()
    }

    @Test
    fun `should return true when person is skjermet`() {
        wireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("true")
                )
        )

        val result = client.erSkjermet("12345678901")

        assertTrue(result)
        wireMock.verify(postRequestedFor(urlEqualTo("/skjermet"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson("""{"personident": "12345678901"}"""))
        )
    }

    @Test
    fun `should return false when person is not skjermet`() {
        wireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("false")
                )
        )

        val result = client.erSkjermet("12345678901")

        assertFalse(result)
    }

    @Test
    fun `should throw exception on error response`() {
        wireMock.stubFor(
            post(urlEqualTo("/skjermet"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")
                )
        )

        assertThrows<RuntimeException> {
            client.erSkjermet("12345678901")
        }
    }
}
