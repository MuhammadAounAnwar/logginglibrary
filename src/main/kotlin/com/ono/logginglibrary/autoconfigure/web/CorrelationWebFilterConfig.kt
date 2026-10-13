package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

/**
 * Auto-configures the [WebFilter] that propagates a correlation ID through reactive pipelines.
 *
 * ## MDC Propagation (Important)
 *
 * This filter writes the correlation ID to **Reactor Context**, which is the correct mechanism
 * for reactive (WebFlux) applications. However, if you need the correlation ID to appear in
 * SLF4J MDC (e.g. `%X{correlationId}` in your Logback pattern), you **must** configure
 * automatic context propagation in your application:
 *
 * 1. Add dependency: `io.micrometer:context-propagation`
 * 2. In your main application class or a `@PostConstruct` bean, call:
 *    ```kotlin
 *    reactor.core.publisher.Hooks.enableAutomaticContextPropagation()
 *    ```
 *
 * Without this, `%X{correlationId}` will always be blank in reactive pipelines.
 */
@Configuration
@ConditionalOnClass(WebFilter::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class CorrelationWebFilterConfig {

    init {
        // Only when context-propagation is on the classpath (it is for every reactive service that enables the Reactor hook).
        if (runCatching { Class.forName("io.micrometer.context.ContextRegistry") }.isSuccess) CorrelationContextPropagation.register()
    }

    @Bean
    @ConditionalOnMissingBean
    fun correlationIdGenerator(): CorrelationIdGenerator = CorrelationIdGenerator.DEFAULT

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(
        prefix = "ono.observability.logging",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun correlationWebFilter(
        properties: ObservabilityProperties,
        generator: CorrelationIdGenerator
    ): WebFilter {
        return WebFilter { exchange: ServerWebExchange, chain: WebFilterChain ->

            val headerName = properties.logging.correlationHeader

            // A valid id from the caller is kept; the alternate header X-Request-ID is read when the canonical one is absent.
            val correlationId = CorrelationIds.resolve(
                exchange.request.headers.getFirst(headerName),
                exchange.request.headers.getFirst(CorrelationIds.ALT_HEADER),
                generator::generate
            )

            // The exchange attribute is what the error handlers read: it does not depend on MDC propagation.
            exchange.attributes[CorrelationIds.ATTRIBUTE] = correlationId
            exchange.response.headers.set(headerName, correlationId)

            chain.filter(exchange)
                .contextWrite { ctx ->
                    ctx.put(MdcKeys.CORRELATION_ID, correlationId)
                }
        }
    }
}
