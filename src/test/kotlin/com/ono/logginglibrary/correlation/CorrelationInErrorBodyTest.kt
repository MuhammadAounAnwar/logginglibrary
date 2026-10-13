package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.exception.DefaultGlobalExceptionHandler
import com.ono.logginglibrary.autoconfigure.exception.DefaultServletExceptionHandler
import com.ono.logginglibrary.autoconfigure.web.correlationId
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.exception.OnoBusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.server.MockServerWebExchange

/**
 * The reported bug: an error body's `correlationId` was read from the REQUEST header, so a caller that sent none (every app
 * request) got null although the service logged under a generated id and put it in the response header. The body now carries the
 * id the filter settled on.
 */
class CorrelationInErrorBodyTest {

    private class Conflict : OnoBusinessException(errorCode = "CONFLICT", httpStatus = HttpStatus.CONFLICT, message = "nope")

    @Test
    fun `servlet error body carries the id the filter generated when the caller sent none`() {
        val request = MockHttpServletRequest().apply { setAttribute(CorrelationIds.ATTRIBUTE, "generated-0001") }

        val body = DefaultServletExceptionHandler().handleBusinessException(Conflict(), request).body!!

        assertThat(body.correlationId).isEqualTo("generated-0001")
    }

    @Test
    fun `reactive error body carries the id the filter generated when the caller sent none`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build())
        exchange.attributes[CorrelationIds.ATTRIBUTE] = "generated-0001"

        val response = DefaultGlobalExceptionHandler().handleBusinessException(Conflict(), exchange).block()!!

        assertThat(response.body!!.correlationId).isEqualTo("generated-0001")
    }

    @Test
    fun `without the filter attribute the MDC is used, then a valid request header`() {
        val request = MockHttpServletRequest().apply { addHeader("X-Correlation-Id", "from-header-0001") }
        assertThat(request.correlationId).isEqualTo("from-header-0001")

        MDC.put(CorrelationIds.MDC_KEY, "from-mdc-00001")
        assertThat(request.correlationId).isEqualTo("from-mdc-00001")
        MDC.remove(CorrelationIds.MDC_KEY)
    }

    @Test
    fun `an invalid header is never reflected into a body`() {
        val request = MockHttpServletRequest().apply { addHeader("X-Correlation-Id", "bad id\nnewline") }
        MDC.remove(CorrelationIds.MDC_KEY)
        assertThat(request.correlationId).isNull()
    }

    @Test
    fun `reactive exchange falls back to the alternate header`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").header("X-Request-ID", "from-gateway-0001").build())
        MDC.remove(CorrelationIds.MDC_KEY)
        assertThat(exchange.correlationId).isEqualTo("from-gateway-0001")
    }
}
