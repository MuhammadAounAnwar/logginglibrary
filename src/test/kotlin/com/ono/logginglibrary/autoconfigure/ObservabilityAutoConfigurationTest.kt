package com.ono.logginglibrary.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ObservabilityAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration::class.java))

    @Test
    fun `ObservabilityProperties is bound with defaults`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(ObservabilityProperties::class.java)
            val props = ctx.getBean(ObservabilityProperties::class.java)
            assertThat(props.enabled).isTrue()
            assertThat(props.logging.enabled).isTrue()
            assertThat(props.logging.correlationHeader).isEqualTo("X-Correlation-Id")
            assertThat(props.metrics.enabled).isTrue()
            assertThat(props.metrics.serviceName).isEqualTo("unknown-service")
            assertThat(props.aop.enabled).isTrue()
            assertThat(props.aop.slowThresholdMs).isEqualTo(500L)
        }
    }

    @Test
    fun `ObservabilityProperties binds custom values`() {
        contextRunner
            .withPropertyValues(
                "ono.observability.logging.correlationHeader=X-Request-Id",
                "ono.observability.metrics.serviceName=my-service",
                "ono.observability.aop.slowThresholdMs=1000"
            )
            .run { ctx ->
                val props = ctx.getBean(ObservabilityProperties::class.java)
                assertThat(props.logging.correlationHeader).isEqualTo("X-Request-Id")
                assertThat(props.metrics.serviceName).isEqualTo("my-service")
                assertThat(props.aop.slowThresholdMs).isEqualTo(1000L)
            }
    }
}