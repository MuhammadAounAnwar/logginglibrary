package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import com.ono.logginglibrary.core.mdc.MdcKeys
import com.ono.logginglibrary.core.mdc.MdcUtils
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

            chain.filter(exchange)
                .contextWrite { ctx ->
                    ctx.put(MdcKeys.CORRELATION_ID, correlationId)
                }
                .doOnEach {
                    MdcUtils.put(MdcKeys.CORRELATION_ID, correlationId)
                }
                .doFinally {
                    MdcUtils.remove(MdcKeys.CORRELATION_ID)
                }
        }
    }
}