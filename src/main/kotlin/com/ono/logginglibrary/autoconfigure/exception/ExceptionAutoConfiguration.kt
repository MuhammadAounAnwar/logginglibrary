package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "ono.observability.exceptions",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(ObservabilityProperties::class)
class ExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun globalExceptionHandler(properties: ObservabilityProperties): DefaultGlobalExceptionHandler {
        return DefaultGlobalExceptionHandler(properties.logging.correlationHeader)
    }
}
