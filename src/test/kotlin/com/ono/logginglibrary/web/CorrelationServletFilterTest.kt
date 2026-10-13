package com.ono.logginglibrary.web

import com.ono.logginglibrary.autoconfigure.web.CorrelationServletFilter
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationServletFilterTest {

    private val headerName = "X-Correlation-Id"

    @Test
    fun `generates correlation ID when header is absent`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator.DEFAULT)
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        var mdcValue: String? = null
        val chain = FilterChain { _, _ ->
            mdcValue = MDC.get(MdcKeys.CORRELATION_ID)
        }

        filter.doFilter(request, response, chain)

        assertThat(response.getHeader(headerName)).isNotNull()
        assertThat(mdcValue).isNotNull()
        assertThat(mdcValue).isEqualTo(response.getHeader(headerName))
        // MDC should be cleared after filter completes
        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull()
    }

    @Test
    fun `uses existing correlation ID from request header`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator.DEFAULT)
        val request = MockHttpServletRequest().apply {
            addHeader(headerName, "test-correlation-123")
        }
        val response = MockHttpServletResponse()

        var mdcValue: String? = null
        val chain = FilterChain { _, _ ->
            mdcValue = MDC.get(MdcKeys.CORRELATION_ID)
        }

        filter.doFilter(request, response, chain)

        assertThat(response.getHeader(headerName)).isEqualTo("test-correlation-123")
        assertThat(mdcValue).isEqualTo("test-correlation-123")
    }

    @Test
    fun `uses custom CorrelationIdGenerator`() {
        val customGenerator = CorrelationIdGenerator { "custom-id-42" }
        val filter = CorrelationServletFilter(headerName, customGenerator)
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val chain = FilterChain { _: ServletRequest, _: ServletResponse -> }

        filter.doFilter(request, response, chain)

        assertThat(response.getHeader(headerName)).isEqualTo("custom-id-42")
    }

    @Test
    fun `clears MDC even when chain throws`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator.DEFAULT)
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = FilterChain { _: ServletRequest, _: ServletResponse ->
            throw RuntimeException("boom")
        }

        try {
            filter.doFilter(request, response, chain)
        } catch (_: RuntimeException) {
            // expected
        }

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull()
    }

    private fun run(filter: CorrelationServletFilter, request: MockHttpServletRequest, response: MockHttpServletResponse): String? {
        var seen: String? = null
        filter.doFilter(request, response, FilterChain { _, _ -> seen = MDC.get(MdcKeys.CORRELATION_ID) })
        return seen
    }

    @Test
    fun `an invalid id from the caller is replaced, not echoed`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })
        val request = MockHttpServletRequest().apply { addHeader(headerName, "bad id\nwith newline") }
        val response = MockHttpServletResponse()

        val seen = run(filter, request, response)

        assertThat(seen).isEqualTo("generated-0001")
        assertThat(response.getHeader(headerName)).isEqualTo("generated-0001")
    }

    @Test
    fun `the alternate X-Request-ID header is used when the canonical one is absent`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })
        val request = MockHttpServletRequest().apply { addHeader("X-Request-ID", "from-gateway-0001") }
        val response = MockHttpServletResponse()

        val seen = run(filter, request, response)

        assertThat(seen).isEqualTo("from-gateway-0001")
        assertThat(response.getHeader(headerName)).isEqualTo("from-gateway-0001")
    }

    @Test
    fun `the canonical header wins over the alternate`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })
        val request = MockHttpServletRequest().apply {
            addHeader(headerName, "canonical-0001")
            addHeader("X-Request-ID", "alternate-0001")
        }
        assertThat(run(filter, request, MockHttpServletResponse())).isEqualTo("canonical-0001")
    }

    @Test
    fun `the id is also a request attribute so error handlers can read it`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })
        val request = MockHttpServletRequest()

        run(filter, request, MockHttpServletResponse())

        assertThat(request.getAttribute(CorrelationIds.ATTRIBUTE)).isEqualTo("generated-0001")
    }

    @Test
    fun `the response carries the id once`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })
        val response = MockHttpServletResponse()

        run(filter, MockHttpServletRequest(), response)

        assertThat(response.getHeaders(headerName)).containsExactly("generated-0001")
    }

    @Test
    fun `the MDC goes back to what it was before the request`() {
        MDC.put(MdcKeys.CORRELATION_ID, "outer-0000001")
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })

        run(filter, MockHttpServletRequest(), MockHttpServletResponse())

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isEqualTo("outer-0000001")
        MDC.remove(MdcKeys.CORRELATION_ID)
    }

    @Test
    fun `the MDC is cleared even when the chain throws`() {
        val filter = CorrelationServletFilter(headerName, CorrelationIdGenerator { "generated-0001" })

        runCatching { filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), FilterChain { _, _ -> error("boom") }) }

        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull()
    }
}
