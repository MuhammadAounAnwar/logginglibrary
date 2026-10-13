package com.ono.logginglibrary.autoconfigure.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.springframework.core.env.Environment
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Role
import org.springframework.core.task.TaskDecorator
import org.springframework.kafka.core.KafkaTemplate

/**
 * Carries the correlation id onward from the place a filter set it: outgoing HTTP calls, threads that run `@Async` work,
 * RabbitMQ messages, Kafka records and (opt in) the database session. Each part is active only when the class it needs is on
 * the classpath, and none of them changes anything when there is no current id.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ono.observability.logging", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class CorrelationPropagationAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.web.client.RestTemplate", "org.springframework.boot.web.client.RestTemplateCustomizer"])
    class RestTemplateConfig {
        @Bean
        @ConditionalOnMissingBean(name = ["correlationRestTemplateCustomizer"])
        fun correlationRestTemplateCustomizer(environment: Environment): RestTemplateCustomizer =
            RestTemplateCustomizer { it.interceptors.add(CorrelationClientHttpRequestInterceptor(headerOf(environment))) }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.web.client.RestClient", "org.springframework.boot.web.client.RestClientCustomizer"])
    class RestClientConfig {
        @Bean
        @ConditionalOnMissingBean(name = ["correlationRestClientCustomizer"])
        fun correlationRestClientCustomizer(environment: Environment): RestClientCustomizer =
            RestClientCustomizer { it.requestInterceptor(CorrelationClientHttpRequestInterceptor(headerOf(environment))) }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.web.reactive.function.client.WebClient", "org.springframework.boot.web.reactive.function.client.WebClientCustomizer"])
    class WebClientConfig {
        @Bean
        @ConditionalOnMissingBean(name = ["correlationWebClientCustomizer"])
        fun correlationWebClientCustomizer(environment: Environment): WebClientCustomizer =
            WebClientCustomizer { it.filter(CorrelationExchangeFilterFunction(headerOf(environment))) }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TaskDecorator::class)
    class AsyncConfig {
        @Bean
        @ConditionalOnMissingBean(TaskDecorator::class)
        fun correlationTaskDecorator(): TaskDecorator = CorrelationTaskDecorator()
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate::class, RabbitTemplateCustomizer::class)
    class RabbitConfig {
        @Bean
        @ConditionalOnMissingBean(name = ["correlationRabbitTemplateCustomizer"])
        fun correlationRabbitTemplateCustomizer(environment: Environment): RabbitTemplateCustomizer =
            RabbitTemplateCustomizer { it.addBeforePublishPostProcessors(CorrelationMessagePostProcessor(headerOf(environment))) }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        fun correlationRabbitFactoryPostProcessor(environment: Environment): BeanPostProcessor =
            CorrelationRabbitFactoryPostProcessor(CorrelationRabbitListenerAdvice(headerOf(environment)))
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate::class)
    class KafkaConfig {
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        fun correlationKafkaPostProcessor(environment: Environment): BeanPostProcessor = CorrelationKafkaPostProcessor(headerOf(environment))
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["javax.sql.DataSource", "org.springframework.jdbc.datasource.DelegatingDataSource"])
    @ConditionalOnProperty(prefix = "ono.observability.correlation", name = ["database-session"], havingValue = "true")
    class DatabaseSessionConfig {
        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        fun correlationDataSourcePostProcessor(): BeanPostProcessor = CorrelationDataSourcePostProcessor()
    }

    private companion object {
        fun headerOf(environment: Environment): String =
            environment.getProperty("ono.observability.logging.correlation-header", CorrelationIds.HEADER)
    }
}
