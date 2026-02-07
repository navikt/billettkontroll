package no.nav.billettkontroll

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

class AzureAdTokenClient(
    private val tokenEndpoint: String = System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT") ?: "",
    private val clientId: String = System.getenv("AZURE_APP_CLIENT_ID") ?: "",
    private val clientSecret: String = System.getenv("AZURE_APP_CLIENT_SECRET") ?: ""
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val objectMapper = jacksonObjectMapper()

    private var cachedToken: String? = null
    private var tokenExpiry: Instant = Instant.MIN

    fun getToken(scope: String): String {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedToken!!
        }
        return fetchToken(scope)
    }

    private fun fetchToken(scope: String): String {
        val formData = mapOf(
            "grant_type" to "client_credentials",
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "scope" to scope
        ).entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, Charsets.UTF_8)}" }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .timeout(Duration.ofSeconds(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            logger.error("Failed to fetch Azure AD token: ${response.statusCode()} ${response.body()}")
            throw RuntimeException("Failed to fetch Azure AD token: ${response.statusCode()}")
        }

        val tokenResponse = objectMapper.readTree(response.body())
        val accessToken = tokenResponse["access_token"].asText()
        val expiresIn = tokenResponse["expires_in"].asLong()

        cachedToken = accessToken
        tokenExpiry = Instant.now().plusSeconds(expiresIn)

        logger.debug("Fetched new Azure AD token, expires in $expiresIn seconds")
        return accessToken
    }
}
