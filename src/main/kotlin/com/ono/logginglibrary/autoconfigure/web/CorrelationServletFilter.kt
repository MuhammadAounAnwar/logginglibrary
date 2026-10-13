package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.core.correlation.CorrelationIds
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
 * Servlet filter that takes the correlation id from the request (or makes one), keeps it in the SLF4J MDC for the logs and in a
 * request attribute for the error handlers, and echoes it in the response header.
 *
 * An id from the caller is accepted only when it is valid ([CorrelationIds.isValid]); the alternate header `X-Request-ID` is
 * read when the canonical one is absent. The MDC is restored to what it was before the request, not just cleared.
 */
class CorrelationServletFilter(
    private val correlationHeader: String,
    private val generator: CorrelationIdGenerator,
    private val alternateHeader: String = CorrelationIds.ALT_HEADER
) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val correlationId = CorrelationIds.resolve(
            httpRequest.getHeader(correlationHeader),
            httpRequest.getHeader(alternateHeader),
            generator::generate
        )

        val previous = MDC.get(MdcKeys.CORRELATION_ID)
        MDC.put(MdcKeys.CORRELATION_ID, correlationId)
        httpRequest.setAttribute(CorrelationIds.ATTRIBUTE, correlationId)
        httpResponse.setHeader(correlationHeader, correlationId)

        try {
            chain.doFilter(request, response)
        } finally {
            if (previous != null) MDC.put(MdcKeys.CORRELATION_ID, previous) else MDC.remove(MdcKeys.CORRELATION_ID)
        }
    }
}
