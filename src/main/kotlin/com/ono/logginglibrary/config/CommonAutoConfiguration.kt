package com.ono.logginglibrary.config

import com.ono.logginglibrary.exception.GlobalExceptionHandler
import com.ono.logginglibrary.logging.LogsConfig
import com.ono.logginglibrary.logging.OriginExtractor
import com.ono.logginglibrary.logging.PerformanceLoggingAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
@EnableAspectJAutoProxy
class CommonAutoConfiguration {

    @Bean
    fun globalExceptionHandler() = GlobalExceptionHandler()

    @Bean
    fun performanceLoggingAspect() = PerformanceLoggingAspect()

    @Bean
    fun logsConfig() = LogsConfig(OriginExtractor())
}