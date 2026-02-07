package no.nav.billettkontroll

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.tjeneste.pip.pipegenansatt.v1.meldinger.ErEgenAnsattEllerIFamilieMedEgenAnsattRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PipEgenAnsattServiceImplTest {

    private val skjermedePersonerClient = mockk<SkjermedePersonerClient>()
    private val service = PipEgenAnsattServiceImpl(skjermedePersonerClient)

    @Test
    fun `should return true when person is skjermet`() {
        val request = ErEgenAnsattEllerIFamilieMedEgenAnsattRequest().apply {
            ident = "12345678901"
        }
        every { skjermedePersonerClient.erSkjermet("12345678901") } returns true

        val response = service.erEgenAnsattEllerIFamilieMedEgenAnsatt(request)

        assertTrue(response.isEgenAnsatt)
        verify { skjermedePersonerClient.erSkjermet("12345678901") }
    }

    @Test
    fun `should return false when person is not skjermet`() {
        val request = ErEgenAnsattEllerIFamilieMedEgenAnsattRequest().apply {
            ident = "12345678901"
        }
        every { skjermedePersonerClient.erSkjermet("12345678901") } returns false

        val response = service.erEgenAnsattEllerIFamilieMedEgenAnsatt(request)

        assertFalse(response.isEgenAnsatt)
    }

    @Test
    fun `should propagate exception from client`() {
        val request = ErEgenAnsattEllerIFamilieMedEgenAnsattRequest().apply {
            ident = "12345678901"
        }
        every { skjermedePersonerClient.erSkjermet(any()) } throws RuntimeException("Connection failed")

        assertThrows(RuntimeException::class.java) {
            service.erEgenAnsattEllerIFamilieMedEgenAnsatt(request)
        }
    }
}
