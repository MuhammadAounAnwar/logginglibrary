package com.ono.logginglibrary.autoconfigure.metrics


import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(MeterRegistry::class)
@ConditionalOnProperty(
    prefix = "ono.observability.metrics",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MicrometerConfiguration(
    private val meterRegistry: MeterRegistry,
    private val properties: ObservabilityProperties
) {

    @PostConstruct
    fun configureCommonTags() {
        meterRegistry.config().commonTags(
            "service", properties.metrics.serviceName
        )
    }
}