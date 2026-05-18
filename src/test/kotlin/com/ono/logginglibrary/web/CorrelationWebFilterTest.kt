package com.ono.logginglibrary.web

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.autoconfigure.web.CorrelationWebFilterConfig
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CorrelationWebFilterTest {

    private val properties = ObservabilityProperties()
    private val headerName = properties.logging.correlationHeader // "X-Correlation-Id"

    private fun buildFilter(generator: CorrelationIdGenerator = CorrelationIdGenerator.DEFAULT) =
        CorrelationWebFilterConfig().correlationWebFilter(properties, generator)

    @Test
    fun `generates correlation ID when header absent and echoes it in response`() {
        val request = MockServerHttpRequest.get("/test").build()
        val exchange = MockServerWebExchange.from(request)

        val chain = WebFilterChain { ex -> Mono.empty() }

        StepVerifier.create(buildFilter().filter(exchange, chain))
            .verifyComplete()

        val responseHeader = exchange.response.headers.getFirst(headerName)
        assertThat(responseHeader).isNotBlank()
        assertThat(responseHeader).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    @Test
    fun `propagates existing correlation ID from request header to response`() {
        val existingId = "existing-correlation-99"
        val request = MockServerHttpRequest.get("/test")
            .header(headerName, existingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(buildFilter().filter(exchange, WebFilterChain { Mono.empty() }))
            .verifyComplete()

        assertThat(exchange.response.headers.getFirst(headerName)).isEqualTo(existingId)
    }

    @Test
    fun `uses custom CorrelationIdGenerator`() {
        val custom = CorrelationIdGenerator { "trace-42" }
        val request = MockServerHttpRequest.get("/test").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(buildFilter(custom).filter(exchange, WebFilterChain { Mono.empty() }))
            .verifyComplete()

        assertThat(exchange.response.headers.getFirst(headerName)).isEqualTo("trace-42")
    }

    @Test
    fun `correlation ID is written to Reactor context`() {
        val fixedId = "ctx-correlation-123"
        val custom = CorrelationIdGenerator { fixedId }
        val request = MockServerHttpRequest.get("/test").build()
        val exchange = MockServerWebExchange.from(request)

        var capturedId: String? = null

        val chain = WebFilterChain { ex ->
            Mono.deferContextual { ctx ->
                capturedId = ctx.getOrDefault(MdcKeys.CORRELATION_ID, null)
                Mono.empty()
            }
        }

        StepVerifier.create(buildFilter(custom).filter(exchange, chain))
            .verifyComplete()

        assertThat(capturedId).isEqualTo(fixedId)
    }

    @Test
    fun `two concurrent requests get independent correlation IDs`() {
        var counter = 0
        val generator = CorrelationIdGenerator { "id-${++counter}" }

        val exchange1 = MockServerWebExchange.from(MockServerHttpRequest.get("/test1").build())
        val exchange2 = MockServerWebExchange.from(MockServerHttpRequest.get("/test2").build())

        val filter = buildFilter(generator)

        StepVerifier.create(filter.filter(exchange1, WebFilterChain { Mono.empty() })).verifyComplete()
        StepVerifier.create(filter.filter(exchange2, WebFilterChain { Mono.empty() })).verifyComplete()

        val id1 = exchange1.response.headers.getFirst(headerName)
        val id2 = exchange2.response.headers.getFirst(headerName)
        assertThat(id1).isNotEqualTo(id2)
    }
}
