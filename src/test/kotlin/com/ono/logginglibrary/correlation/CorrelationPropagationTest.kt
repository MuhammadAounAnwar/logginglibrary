package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.correlation.CorrelationClientHttpRequestInterceptor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationExchangeFilterFunction
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationTaskDecorator
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.correlation.CorrelationPropagation
import com.ono.logginglibrary.core.correlation.withCorrelation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class CorrelationPropagationTest {

    @AfterEach
    fun clean() = MDC.clear()

    // ── threads ─────────────────────────────────────────────────────────────────

    @Test
    fun `a wrapped task keeps the id of the thread that wrapped it, on another thread`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        val pool = Executors.newSingleThreadExecutor()
        val seen = CompletableFuture<String?>()

        pool.execute(Runnable { seen.complete(MDC.get(CorrelationIds.MDC_KEY)) }.withCorrelation())

        assertThat(seen.get()).isEqualTo("request-0001")
        pool.shutdown()
    }

    @Test
    fun `an unwrapped task on a pool thread has no id, which is the problem the wrapper solves`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        val pool = Executors.newSingleThreadExecutor()
        val seen = CompletableFuture<String?>()

        pool.execute { seen.complete(MDC.get(CorrelationIds.MDC_KEY)) }

        assertThat(seen.get()).isNull()
        pool.shutdown()
    }

    @Test
    fun `the pool thread gets its own previous value back after the task`() {
        val pool = Executors.newSingleThreadExecutor()
        pool.submit { MDC.put(CorrelationIds.MDC_KEY, "pool-own-0001") }.get()
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")

        pool.submit(Callable { MDC.get(CorrelationIds.MDC_KEY) }.withCorrelation()).get()

        assertThat(pool.submit(Callable { MDC.get(CorrelationIds.MDC_KEY) }).get()).isEqualTo("pool-own-0001")
        pool.shutdown()
    }

    @Test
    fun `withId with no id leaves the thread alone`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        assertThat(CorrelationPropagation.withId(null) { MDC.get(CorrelationIds.MDC_KEY) }).isEqualTo("request-0001")
    }

    @Test
    fun `an executor wrapper carries the id of each submitter`() {
        val pool = Executors.newFixedThreadPool(2)
        val wrapped = CorrelationPropagation.wrap(pool)
        val a = CompletableFuture<String?>(); val b = CompletableFuture<String?>()

        MDC.put(CorrelationIds.MDC_KEY, "request-000A")
        wrapped.execute { a.complete(MDC.get(CorrelationIds.MDC_KEY)) }
        MDC.put(CorrelationIds.MDC_KEY, "request-000B")
        wrapped.execute { b.complete(MDC.get(CorrelationIds.MDC_KEY)) }

        assertThat(a.get()).isEqualTo("request-000A")
        assertThat(b.get()).isEqualTo("request-000B")
        pool.shutdown()
    }

    @Test
    fun `the Spring task decorator copies the MDC onto the async thread and restores it`() {
        val executor = ThreadPoolTaskExecutor().apply { setTaskDecorator(CorrelationTaskDecorator()); corePoolSize = 1; initialize() }
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        MDC.put("userId", "u-7")
        val seen = CompletableFuture<List<String?>>()

        executor.execute { seen.complete(listOf(MDC.get(CorrelationIds.MDC_KEY), MDC.get("userId"))) }

        assertThat(seen.get()).containsExactly("request-0001", "u-7")
        val after = CompletableFuture<String?>()
        MDC.clear()
        executor.execute { after.complete(MDC.get(CorrelationIds.MDC_KEY)) }
        assertThat(after.get()).isNull()
        executor.shutdown()
    }

    // ── HTTP clients ────────────────────────────────────────────────────────────

    private fun call(interceptor: CorrelationClientHttpRequestInterceptor, preset: String? = null): MockClientHttpRequest {
        val request = MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x"))
        preset?.let { request.headers.set(CorrelationIds.HEADER, it) }
        val execution = ClientHttpRequestExecution { _, _ -> MockClientHttpResponse(ByteArray(0), HttpStatus.OK) as ClientHttpResponse }
        interceptor.intercept(request, ByteArray(0), execution)
        return request
    }

    @Test
    fun `a blocking client call carries the current id`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        assertThat(call(CorrelationClientHttpRequestInterceptor()).headers.getFirst(CorrelationIds.HEADER)).isEqualTo("request-0001")
    }

    @Test
    fun `a blocking client call does not overwrite an id the caller set, and adds nothing without a current id`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        assertThat(call(CorrelationClientHttpRequestInterceptor(), preset = "explicit-0001").headers.getFirst(CorrelationIds.HEADER)).isEqualTo("explicit-0001")
        MDC.clear()
        assertThat(call(CorrelationClientHttpRequestInterceptor()).headers.containsKey(CorrelationIds.HEADER)).isFalse()
    }

    private fun exchange(captured: MutableList<ClientRequest>) = ExchangeFunction { req ->
        captured += req
        Mono.just(ClientResponse.create(HttpStatus.OK).build())
    }

    @Test
    fun `a WebClient call carries the id from the Reactor context even when the MDC is empty`() {
        val captured = mutableListOf<ClientRequest>()
        val request = ClientRequest.create(HttpMethod.GET, URI.create("http://svc/x")).build()

        StepVerifier.create(
            CorrelationExchangeFilterFunction().filter(request, exchange(captured))
                .contextWrite { it.put(CorrelationIds.MDC_KEY, "from-context-01") }
        ).expectNextCount(1).verifyComplete()

        assertThat(captured.single().headers().getFirst(CorrelationIds.HEADER)).isEqualTo("from-context-01")
    }

    @Test
    fun `a WebClient call falls back to the MDC and keeps an id the caller set`() {
        val captured = mutableListOf<ClientRequest>()
        MDC.put(CorrelationIds.MDC_KEY, "from-mdc-00001")
        StepVerifier.create(CorrelationExchangeFilterFunction().filter(ClientRequest.create(HttpMethod.GET, URI.create("http://svc/x")).build(), exchange(captured)))
            .expectNextCount(1).verifyComplete()
        assertThat(captured.last().headers().getFirst(CorrelationIds.HEADER)).isEqualTo("from-mdc-00001")

        val preset = ClientRequest.create(HttpMethod.GET, URI.create("http://svc/x")).header(CorrelationIds.HEADER, "explicit-0001").build()
        StepVerifier.create(CorrelationExchangeFilterFunction().filter(preset, exchange(captured))).expectNextCount(1).verifyComplete()
        assertThat(captured.last().headers().getFirst(CorrelationIds.HEADER)).isEqualTo("explicit-0001")
    }

    @Test
    fun `a WebClient call with no id anywhere is sent as it is`() {
        val captured = mutableListOf<ClientRequest>()
        StepVerifier.create(CorrelationExchangeFilterFunction().filter(ClientRequest.create(HttpMethod.GET, URI.create("http://svc/x")).build(), exchange(captured)))
            .expectNextCount(1).verifyComplete()
        assertThat(captured.single().headers().containsKey(CorrelationIds.HEADER)).isFalse()
    }
}
