package no.nav.billettkontroll

import com.sun.net.httpserver.Filter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import jakarta.xml.ws.Endpoint
import jakarta.xml.ws.handler.soap.SOAPHandler
import jakarta.xml.ws.handler.soap.SOAPMessageContext
import jakarta.xml.ws.soap.SOAPBinding
import no.nav.tjeneste.pip.pipegenansatt.v1.PipEgenAnsattPortType
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

private val logger = LoggerFactory.getLogger("Application")
private val accessLog = LoggerFactory.getLogger("AccessLog")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val (httpServer, endpoint) = startServer(port, PipEgenAnsattServiceImpl(), SecurityHeaderHandler())

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        endpoint.stop()
        httpServer.stop(0)
    })

    logger.info("Server started on port $port")
    logger.info("SOAP endpoint: http://localhost:$port/services/pipEgenAnsatt?wsdl")
    logger.info("Health endpoint: http://localhost:$port/internal/health/liveness")

    Thread.currentThread().join()
}

fun startServer(
    port: Int,
    service: PipEgenAnsattPortType,
    securityHandler: SOAPHandler<SOAPMessageContext>
): Pair<HttpServer, Endpoint> {
    val httpServer = HttpServer.create(InetSocketAddress(port), 0)

    httpServer.createContext("/internal/health/liveness") { exchange ->
        val response = """{"status":"UP"}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, response.length.toLong())
        exchange.responseBody.use { it.write(response.toByteArray()) }
    }

    val soapContext = httpServer.createContext("/services/pipEgenAnsatt")
    soapContext.filters.add(AccessLogFilter())
    val endpoint = Endpoint.create(service)
    val binding = endpoint.binding as SOAPBinding
    binding.handlerChain = listOf(securityHandler)
    endpoint.publish(soapContext)

    httpServer.start()
    return Pair(httpServer, endpoint)
}

class AccessLogFilter : Filter() {
    override fun doFilter(exchange: HttpExchange, chain: Chain) {
        val start = System.currentTimeMillis()
        try {
            chain.doFilter(exchange)
        } finally {
            val duration = System.currentTimeMillis() - start
            val clientIp = exchange.remoteAddress.address.hostAddress
            val method = exchange.requestMethod
            val path = exchange.requestURI.path
            val query = exchange.requestURI.query?.let { "?$it" } ?: ""
            val status = exchange.responseCode
            accessLog.info("{} {} {}{} {} {}ms", clientIp, method, path, query, status, duration)
        }
    }

    override fun description(): String = "Access Log Filter"
}
