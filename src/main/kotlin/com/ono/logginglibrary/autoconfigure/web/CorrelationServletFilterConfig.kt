package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Auto-configures the servlet [CorrelationServletFilter] for Servlet/MVC applications.
 * Registered at highest precedence so correlation IDs are available to all downstream filters.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
    prefix = "ono.observability.logging",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class CorrelationServletFilterConfig {

    @Bean
    @ConditionalOnMissingBean
    fun correlationIdGenerator(): CorrelationIdGenerator = CorrelationIdGenerator.DEFAULT

    @Bean
    fun correlationServletFilterRegistration(
        properties: ObservabilityProperties,
        generator: CorrelationIdGenerator
    ): FilterRegistrationBean<CorrelationServletFilter> {
        val filter = CorrelationServletFilter(
            correlationHeader = properties.logging.correlationHeader,
            generator = generator
        )
        return FilterRegistrationBean(filter).apply {
            order = Ordered.HIGHEST_PRECEDENCE
            addUrlPatterns("/*")
        }
    }
}
