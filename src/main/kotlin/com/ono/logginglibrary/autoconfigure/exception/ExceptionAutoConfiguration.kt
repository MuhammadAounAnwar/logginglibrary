package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ono.observability.exceptions",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(ObservabilityProperties::class)
class ExceptionAutoConfiguration {

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    class ReactiveExceptionConfig {

        @Bean
        @ConditionalOnMissingBean
        fun globalExceptionHandler(properties: ObservabilityProperties): DefaultGlobalExceptionHandler {
            return DefaultGlobalExceptionHandler(properties.logging.correlationHeader)
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    class ServletExceptionConfig {

        @Bean
        @ConditionalOnMissingBean
        fun servletExceptionHandler(properties: ObservabilityProperties): DefaultServletExceptionHandler {
            return DefaultServletExceptionHandler(properties.logging.correlationHeader)
        }
    }
}
