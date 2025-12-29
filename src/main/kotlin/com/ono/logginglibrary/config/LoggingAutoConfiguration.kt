package com.ono.logginglibrary.config

import com.ono.logginglibrary.exception.GlobalExceptionHandler
import com.ono.logginglibrary.logging.LogsConfig
import com.ono.logginglibrary.logging.OriginExtractor
import com.ono.logginglibrary.logging.PerformanceLoggingAspect
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.zalando.logbook.Logbook

@AutoConfiguration
class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun originExtractor() = OriginExtractor()

    @Bean
    @ConditionalOnMissingBean
    fun logbook(originExtractor: OriginExtractor): Logbook =
        LogsConfig(originExtractor).provideLogBook()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "ono.logging.performance",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun performanceLoggingAspect() = PerformanceLoggingAspect()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "ono.logging.exceptions",
        name = ["enabled"],
        havingValue = "true"
    )
    fun globalExceptionHandler() = GlobalExceptionHandler()
}
