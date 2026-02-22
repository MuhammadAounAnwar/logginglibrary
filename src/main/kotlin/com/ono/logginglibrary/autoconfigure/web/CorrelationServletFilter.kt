package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC

/**
 * Servlet filter that extracts or generates a correlation ID per request,
 * places it in SLF4J MDC for logging, and echoes it in the response header.
 */
class CorrelationServletFilter(
    private val correlationHeader: String,
    private val generator: CorrelationIdGenerator
) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val correlationId = httpRequest.getHeader(correlationHeader)
            ?: generator.generate()

        MDC.put(MdcKeys.CORRELATION_ID, correlationId)
        httpResponse.setHeader(correlationHeader, correlationId)

        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID)
        }
    }
}
