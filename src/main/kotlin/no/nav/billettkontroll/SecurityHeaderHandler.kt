package no.nav.billettkontroll

import jakarta.xml.soap.SOAPFactory
import jakarta.xml.soap.SOAPMessage
import jakarta.xml.ws.handler.MessageContext
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import jakarta.xml.ws.soap.SOAPFaultException
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.namespace.QName

private val logger = LoggerFactory.getLogger(SecurityHeaderHandler::class.java)

private const val SAML2_NS = "urn:oasis:names:tc:SAML:2.0:assertion"
private const val SAML1_NS = "urn:oasis:names:tc:SAML:1.0:assertion"
private const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
private const val WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
private const val SOAP_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/"

class SecurityHeaderHandler(
    private val allowedUsernames: Set<String> = System.getenv("ALLOWED_USERNAMES")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: emptySet()
) : SOAPHandler<SOAPMessageContext> {

    companion object {
        private val SECURITY_HEADER = QName(WSSE_NS, "Security")
    }

    override fun getHeaders(): Set<QName> = setOf(SECURITY_HEADER)

    override fun handleMessage(context: SOAPMessageContext): Boolean {
        val outbound = context[MessageContext.MESSAGE_OUTBOUND_PROPERTY] as Boolean
        if (!outbound) {
            validateSecurityHeader(context.message)
        }
        return true
    }

    override fun handleFault(context: SOAPMessageContext): Boolean = true

    override fun close(context: MessageContext) {}

    private fun validateSecurityHeader(message: SOAPMessage) {
        try {
            val header = message.soapHeader
            if (header == null) {
                logger.warn("Request rejected: no SOAP header present")
                rejectWithFault("Request rejected: no SOAP header present")
            }

            val securityHeaders = header.getChildElements(SECURITY_HEADER)
            if (!securityHeaders.hasNext()) {
                logger.warn("Request rejected: no WS-Security header present")
                rejectWithFault("Request rejected: no WS-Security header present")
            }

            val securityElement = securityHeaders.next() as Element
            val assertion = findAssertion(securityElement)

            if (assertion == null) {
                val usernameToken = findFirst(securityElement, WSSE_NS, "UsernameToken")
                if (usernameToken != null) {
                    val username = getElementText(usernameToken, WSSE_NS, "Username")
                    if (username != null && username in allowedUsernames) {
                        logger.info("Request authorized: {}", kv("wsse_username", username))
                        return
                    }
                    logger.warn("Request rejected: {}", kv("wsse_username", username ?: "empty"))
                    rejectWithFault("Request rejected: unauthorized username")
                }
                val childElements = buildList {
                    val children = securityElement.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child is Element) {
                            add("{${child.namespaceURI}}${child.localName}")
                        }
                    }
                }
                logger.warn(
                    "Request rejected, unknown security content: {}",
                    kv("security_children", childElements)
                )
                rejectWithFault("Request rejected: unknown security content")
            }

            val samlNs = assertion.namespaceURI
            val issuer = getElementText(assertion, samlNs, "Issuer")
            val nameId = findNameId(assertion, samlNs)
            val conditions = findFirst(assertion, samlNs, "Conditions")
            val notBefore = conditions?.getAttribute("NotBefore")
            val notOnOrAfter = conditions?.getAttribute("NotOnOrAfter")
            val audience = findAudience(conditions, samlNs)
            val authnContext = findAuthnContextClassRef(assertion, samlNs)
            val signatureAlgorithm = findSignatureAlgorithm(assertion)

            logger.info(
                "SAML assertion details: {}, {}, {}, {}, {}, {}, {}",
                kv("saml_issuer", issuer ?: "unknown"),
                kv("saml_subject", nameId ?: "unknown"),
                kv("saml_not_before", notBefore ?: "not set"),
                kv("saml_not_on_or_after", notOnOrAfter ?: "not set"),
                kv("saml_audience", audience ?: "not set"),
                kv("saml_authn_context", authnContext ?: "not set"),
                kv("saml_signed", if (signatureAlgorithm != null) "yes ($signatureAlgorithm)" else "no")
            )
        } catch (e: SOAPFaultException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error processing security header, rejecting request", e)
            rejectWithFault("Error processing security header")
        }
    }

    private fun rejectWithFault(message: String): Nothing {
        val fault = SOAPFactory.newInstance().createFault(
            message,
            QName(SOAP_ENV_NS, "Client")
        )
        throw SOAPFaultException(fault)
    }

    private fun findAssertion(securityElement: Element): Element? {
        for (ns in listOf(SAML2_NS, SAML1_NS)) {
            val assertions = securityElement.getElementsByTagNameNS(ns, "Assertion")
            if (assertions.length > 0) return assertions.item(0) as Element
        }
        return null
    }

    private fun findNameId(assertion: Element, ns: String): String? {
        val subject = findFirst(assertion, ns, "Subject") ?: return null
        return getElementText(subject, ns, "NameID")
    }

    private fun findAudience(conditions: Element?, ns: String): String? {
        if (conditions == null) return null
        val restriction = findFirst(conditions, ns, "AudienceRestriction") ?: return null
        return getElementText(restriction, ns, "Audience")
    }

    private fun findAuthnContextClassRef(assertion: Element, ns: String): String? {
        val authnStatement = findFirst(assertion, ns, "AuthnStatement") ?: return null
        val authnContext = findFirst(authnStatement, ns, "AuthnContext") ?: return null
        return getElementText(authnContext, ns, "AuthnContextClassRef")
    }

    private fun findSignatureAlgorithm(assertion: Element): String? {
        val signedInfo = findFirst(assertion, XMLDSIG_NS, "SignedInfo") ?: return null
        val signatureMethod = findFirst(signedInfo, XMLDSIG_NS, "SignatureMethod") ?: return null
        return signatureMethod.getAttribute("Algorithm")
    }

    private fun findFirst(parent: Element, ns: String, localName: String): Element? {
        val nodes: NodeList = parent.getElementsByTagNameNS(ns, localName)
        return if (nodes.length > 0) nodes.item(0) as Element else null
    }

    private fun getElementText(parent: Element, ns: String, localName: String): String? {
        val element = findFirst(parent, ns, localName) ?: return null
        return element.textContent?.trim()
    }
}
