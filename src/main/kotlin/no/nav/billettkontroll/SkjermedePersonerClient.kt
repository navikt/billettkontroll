package no.nav.billettkontroll

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class SkjermedePersonerClient(
    private val baseUrl: String = System.getenv("SKJERMEDE_PERSONER_URL"),
    private val scope: String = System.getenv("SKJERMEDE_PERSONER_SCOPE"),
    private val tokenClient: AzureAdTokenClient = AzureAdTokenClient()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val objectMapper = jacksonObjectMapper()

    fun erSkjermet(personident: String): Boolean {
        val token = tokenClient.getToken(scope)

        val requestBody = objectMapper.writeValueAsString(mapOf("personident" to personident))

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/skjermet"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            logger.error("Skjermede-personer-pip returned ${response.statusCode()}: ${response.body()}")
            throw RuntimeException("Failed to check skjerming: ${response.statusCode()}")
        }

        return response.body().toBoolean()
    }
}
