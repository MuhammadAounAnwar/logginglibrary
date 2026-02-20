package com.ono.logginglibrary.autoconfigure

import com.ono.logginglibrary.autoconfigure.aop.ObservabilityAspect
import com.ono.logginglibrary.autoconfigure.metrics.MicrometerConfiguration
import com.ono.logginglibrary.autoconfigure.web.CorrelationWebFilterConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties::class)
@ConditionalOnProperty(
    prefix = "ono.observability",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@Import(
    CorrelationWebFilterConfig::class,
    MicrometerConfiguration::class,
    ObservabilityAspect::class
)
class ObservabilityAutoConfiguration