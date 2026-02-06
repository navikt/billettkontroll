package no.nav.billettkontroll

import jakarta.xml.soap.SOAPMessage
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import org.slf4j.LoggerFactory
import javax.xml.namespace.QName

private val logger = LoggerFactory.getLogger(SecurityHeaderHandler::class.java)

class SecurityHeaderHandler : SOAPHandler<SOAPMessageContext> {

    companion object {
        private val WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        private val SECURITY_HEADER = QName(WSSE_NS, "Security")
    }

    override fun getHeaders(): Set<QName> = setOf(SECURITY_HEADER)

    override fun handleMessage(context: SOAPMessageContext): Boolean {
        val outbound = context[MessageContext.MESSAGE_OUTBOUND_PROPERTY] as Boolean
        if (!outbound) {
            logSecurityHeader(context.message)
        }
        return true
    }

    override fun handleFault(context: SOAPMessageContext): Boolean = true

    override fun close(context: MessageContext) {}

    private fun logSecurityHeader(message: SOAPMessage) {
        try {
            val header = message.soapHeader ?: return
            val securityHeaders = header.getChildElements(SECURITY_HEADER)
            if (securityHeaders.hasNext()) {
                logger.debug("WS-Security header present and acknowledged")
            }
        } catch (e: Exception) {
            logger.warn("Error processing security header", e)
        }
    }
}
