package com.ono.logginglibrary.autoconfigure.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

/** Adds the current correlation id to a blocking HTTP call (`RestTemplate`, `RestClient`) unless the caller already set one. */
class CorrelationClientHttpRequestInterceptor(private val header: String = CorrelationIds.HEADER) : ClientHttpRequestInterceptor {
    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        if (!request.headers.containsKey(header)) {
            CorrelationIds.current()?.let { request.headers.set(header, it) }
        }
        return execution.execute(request, body)
    }
}

/**
 * Adds the correlation id to a `WebClient` call. In a reactive chain the id is in the Reactor context (put there by the web
 * filter) and, with context propagation enabled, in the MDC; the context is read first because it does not depend on the thread.
 */
class CorrelationExchangeFilterFunction(private val header: String = CorrelationIds.HEADER) : ExchangeFilterFunction {
    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        Mono.deferContextual { ctx ->
            val id = ctx.getOrDefault<String?>(CorrelationIds.MDC_KEY, null) ?: CorrelationIds.current()
            if (id == null || request.headers().containsKey(header)) next.exchange(request)
            else next.exchange(ClientRequest.from(request).header(header, id).build())
        }
}
