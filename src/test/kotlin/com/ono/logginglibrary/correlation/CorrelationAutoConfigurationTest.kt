package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.correlation.CorrelationDataSourcePostProcessor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationPropagationAutoConfiguration
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationTaskDecorator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator

class CorrelationAutoConfigurationTest {

    private val runner = ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CorrelationPropagationAutoConfiguration::class.java))

    @Test
    fun `the client customizers, task decorator and Rabbit customizer are there by default`() {
        runner.run { ctx ->
            assertThat(ctx).hasSingleBean(RestTemplateCustomizer::class.java)
            assertThat(ctx).hasSingleBean(RestClientCustomizer::class.java)
            assertThat(ctx).hasSingleBean(WebClientCustomizer::class.java)
            assertThat(ctx).hasSingleBean(TaskDecorator::class.java)
            assertThat(ctx).hasSingleBean(RabbitTemplateCustomizer::class.java)
            assertThat(ctx.getBean(TaskDecorator::class.java)).isInstanceOf(CorrelationTaskDecorator::class.java)
        }
    }

    @Test
    fun `logging switched off switches all of it off`() {
        runner.withPropertyValues("ono.observability.logging.enabled=false").run { ctx ->
            assertThat(ctx).doesNotHaveBean(TaskDecorator::class.java)
            assertThat(ctx).doesNotHaveBean(RestTemplateCustomizer::class.java)
        }
    }

    @Configuration
    class OwnDecorator {
        @Bean fun own(): TaskDecorator = TaskDecorator { it }
    }

    @Test
    fun `a service's own task decorator wins`() {
        runner.withUserConfiguration(OwnDecorator::class.java).run { ctx ->
            assertThat(ctx).hasSingleBean(TaskDecorator::class.java)
            assertThat(ctx.getBean(TaskDecorator::class.java)).isNotInstanceOf(CorrelationTaskDecorator::class.java)
        }
    }

    @Test
    fun `the database session hook is off unless asked for`() {
        runner.run { ctx -> assertThat(ctx.getBeanNamesForType(CorrelationDataSourcePostProcessor::class.java)).isEmpty() }
        runner.withPropertyValues("ono.observability.correlation.database-session=true").run { ctx ->
            assertThat(ctx.getBeansOfType(org.springframework.beans.factory.config.BeanPostProcessor::class.java).values)
                .anyMatch { it is CorrelationDataSourcePostProcessor }
        }
    }

    @Test
    fun `the messaging hooks are registered`() {
        runner.run { ctx ->
            val names = ctx.getBeansOfType(org.springframework.beans.factory.config.BeanPostProcessor::class.java).values.map { it.javaClass.simpleName }
            assertThat(names).contains("CorrelationKafkaPostProcessor", "CorrelationRabbitFactoryPostProcessor")
        }
    }

    @Test
    fun `a configured header name is used by the outgoing hooks`() {
        runner.withPropertyValues("ono.observability.logging.correlation-header=X-Trace-Key").run { ctx ->
            val template = org.springframework.web.client.RestTemplate()
            ctx.getBean(RestTemplateCustomizer::class.java).customize(template)
            val interceptor = template.interceptors.filterIsInstance<com.ono.logginglibrary.autoconfigure.correlation.CorrelationClientHttpRequestInterceptor>().single()
            val header = org.springframework.test.util.ReflectionTestUtils.getField(interceptor, "header")
            assertThat(header).isEqualTo("X-Trace-Key")
        }
    }
}
