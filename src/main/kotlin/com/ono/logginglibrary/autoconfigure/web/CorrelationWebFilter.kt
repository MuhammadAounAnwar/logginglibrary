package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

@Configuration
@ConditionalOnClass(WebFilter::class)
class CorrelationWebFilterConfig {

    @Bean
    @ConditionalOnProperty(
        prefix = "ono.observability.logging",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun correlationWebFilter(
        properties: ObservabilityProperties
    ): WebFilter {

        val generator = CorrelationIdGenerator.DEFAULT

        return WebFilter { exchange: ServerWebExchange, chain: WebFilterChain ->

            val headerName = properties.logging.correlationHeader

            val correlationId =
                exchange.request.headers.getFirst(headerName)
                    ?: generator.generate()

            exchange.response.headers.add(headerName, correlationId)

            // Propagate via Reactor Context, which is the correct mechanism for reactive pipelines.
            // For MDC propagation (thread-local), configure Hooks.enableAutomaticContextPropagation()
            // with micrometer-context-propagation in your application.
            chain.filter(exchange)
                .contextWrite { ctx ->
                    ctx.put(MdcKeys.CORRELATION_ID, correlationId)
                }
        }
    }
}
