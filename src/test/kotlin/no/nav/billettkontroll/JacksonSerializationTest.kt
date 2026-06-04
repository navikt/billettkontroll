package no.nav.billettkontroll

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Verifiserer at jackson-module-kotlin fungerer som forventet.
 * Disse testene er spesielt nyttige ved oppgradering av jackson-module-kotlin,
 * f.eks. PR #29 (2.21.0 → 2.21.3).
 */
class JacksonSerializationTest {

    private val objectMapper = jacksonObjectMapper()

    // Data class som brukes til å teste at KotlinModule er korrekt registrert.
    // Uten KotlinModule vil Jackson ikke kunne deserialisere data-klasser
    // som mangler no-arg constructor.
    data class TokenResponse(val access_token: String, val expires_in: Long)

    @Test
    fun `jacksonObjectMapper deserialiserer Kotlin data class via konstruktør`() {
        val json = """{"access_token": "test-token", "expires_in": 3600}"""

        val result = objectMapper.readValue<TokenResponse>(json)

        assertEquals("test-token", result.access_token)
        assertEquals(3600L, result.expires_in)
    }

    @Test
    fun `readTree leser access_token og expires_in slik AzureAdTokenClient gjør det`() {
        val json = """{"access_token": "test-token", "expires_in": 3600}"""

        val tree = objectMapper.readTree(json)

        assertEquals("test-token", tree["access_token"].asText())
        assertEquals(3600L, tree["expires_in"].asLong())
    }

    @Test
    fun `readTree tolerer ukjente felter i token-respons`() {
        val json = """{"access_token": "tok", "expires_in": 3600, "token_type": "Bearer", "scope": "api://test/.default"}"""

        val tree = objectMapper.readTree(json)

        assertEquals("tok", tree["access_token"].asText())
        assertEquals(3600L, tree["expires_in"].asLong())
    }

    @Test
    fun `readTree håndterer stor expires_in-verdi (større enn Int MAX_VALUE)`() {
        val largeExpiresIn = Int.MAX_VALUE.toLong() + 1
        val json = """{"access_token": "tok", "expires_in": $largeExpiresIn}"""

        val tree = objectMapper.readTree(json)

        assertEquals(largeExpiresIn, tree["expires_in"].asLong())
    }

    @Test
    fun `writeValueAsString serialiserer Map til JSON slik SkjermedePersonerClient gjør det`() {
        val map = mapOf("personident" to "12345678901")

        val json = objectMapper.writeValueAsString(map)

        val parsed = objectMapper.readTree(json)
        assertEquals("12345678901", parsed["personident"].asText())
    }

}
