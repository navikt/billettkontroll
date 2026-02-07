package no.nav.billettkontroll

import jakarta.jws.WebService
import no.nav.tjeneste.pip.pipegenansatt.v1.PipEgenAnsattPortType
import no.nav.tjeneste.pip.pipegenansatt.v1.meldinger.ErEgenAnsattEllerIFamilieMedEgenAnsattRequest
import no.nav.tjeneste.pip.pipegenansatt.v1.meldinger.ErEgenAnsattEllerIFamilieMedEgenAnsattResponse
import org.slf4j.LoggerFactory

@WebService(
    serviceName = "PipEgenAnsatt_v1",
    portName = "PipEgenAnsatt_v1",
    targetNamespace = "http://nav.no/tjeneste/pip/pipEgenAnsatt/v1/",
    endpointInterface = "no.nav.tjeneste.pip.pipegenansatt.v1.PipEgenAnsattPortType"
)
class PipEgenAnsattServiceImpl(
    private val skjermedePersonerClient: SkjermedePersonerClient = SkjermedePersonerClient()
) : PipEgenAnsattPortType {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun erEgenAnsattEllerIFamilieMedEgenAnsatt(
        request: ErEgenAnsattEllerIFamilieMedEgenAnsattRequest
    ) = ErEgenAnsattEllerIFamilieMedEgenAnsattResponse().apply {
        isEgenAnsatt = try {
            skjermedePersonerClient.erSkjermet(request.ident)
        } catch (e: Exception) {
            logger.error("Feil ved kall til skjermede-personer-pip for ident", e)
            throw e
        }
    }
}
