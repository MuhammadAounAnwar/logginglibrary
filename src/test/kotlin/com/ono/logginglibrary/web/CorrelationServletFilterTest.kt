package com.ono.logginglibrary.web

import com.ono.logginglibrary.autoconfigure.web.CorrelationServletFilter
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
}
