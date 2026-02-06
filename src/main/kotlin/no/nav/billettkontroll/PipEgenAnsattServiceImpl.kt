package no.nav.billettkontroll

import jakarta.jws.WebService
import no.nav.tjeneste.pip.pipegenansatt.v1.PipEgenAnsattPortType
import no.nav.tjeneste.pip.pipegenansatt.v1.meldinger.ErEgenAnsattEllerIFamilieMedEgenAnsattRequest
import no.nav.tjeneste.pip.pipegenansatt.v1.meldinger.ErEgenAnsattEllerIFamilieMedEgenAnsattResponse

@WebService(
    serviceName = "PipEgenAnsatt_v1",
    portName = "PipEgenAnsatt_v1",
    targetNamespace = "http://nav.no/tjeneste/pip/pipEgenAnsatt/v1/",
    endpointInterface = "no.nav.tjeneste.pip.pipegenansatt.v1.PipEgenAnsattPortType"
)
class PipEgenAnsattServiceImpl : PipEgenAnsattPortType {

    override fun erEgenAnsattEllerIFamilieMedEgenAnsatt(
        request: ErEgenAnsattEllerIFamilieMedEgenAnsattRequest
    ): ErEgenAnsattEllerIFamilieMedEgenAnsattResponse {
        return ErEgenAnsattEllerIFamilieMedEgenAnsattResponse().apply {
            // TODO: Implementer logikk for å sjekke om personen er egen ansatt
            setEgenAnsatt(false)
        }
    }
}
