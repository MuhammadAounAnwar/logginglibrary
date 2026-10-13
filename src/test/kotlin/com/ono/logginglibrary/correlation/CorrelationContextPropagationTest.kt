package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.web.CorrelationContextPropagation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class CorrelationContextPropagationTest {

    @Test
    fun `the id in the Reactor context is in the MDC of the thread that logs, and gone afterwards`() {
        Hooks.enableAutomaticContextPropagation()
        CorrelationContextPropagation.register()
        CorrelationContextPropagation.register() // idempotent

        val seen = Mono.just("x")
            .publishOn(Schedulers.boundedElastic())
            .map { MDC.get("correlationId") }
            .contextWrite { it.put("correlationId", "request-from-app-0001") }
            .block()

        assertThat(seen).isEqualTo("request-from-app-0001")
        assertThat(MDC.get("correlationId")).isNull()
    }
}
