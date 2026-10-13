package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.correlation.CorrelationPropagationAutoConfiguration
import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The correlation id through real brokers: a message sent while a request's id is in the MDC reaches the listener with that id
 * in ITS MDC, and the listener thread is left as it was found.
 */
@Testcontainers(disabledWithoutDocker = true)
class CorrelationBrokerIntegrationTest {

    class Seen(val id: String?)

    @Configuration
    @EnableRabbit
    @EnableKafka
    class Listeners {
        @Bean fun queue() = Queue("cid-test", false, false, true)

        @RabbitListener(queues = ["cid-test"])
        fun onRabbit(body: String) {
            seenRabbit.complete(Seen(MDC.get(CorrelationIds.MDC_KEY)))
        }

        @KafkaListener(topics = ["cid-test"], groupId = "cid-test")
        fun onKafka(body: String) {
            seenKafka.complete(Seen(MDC.get(CorrelationIds.MDC_KEY)))
        }
    }

    companion object {
        @Container @JvmStatic
        val rabbit = RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))

        @Container @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.8")).withKraft()

        @JvmStatic var seenRabbit = CompletableFuture<Seen>()
        @JvmStatic var seenKafka = CompletableFuture<Seen>()
    }

    @AfterEach
    fun clean() = MDC.clear()

    private fun runner() = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration::class.java, KafkaAutoConfiguration::class.java, CorrelationPropagationAutoConfiguration::class.java))
        .withUserConfiguration(Listeners::class.java)
        .withPropertyValues(
            "spring.rabbitmq.host=${rabbit.host}", "spring.rabbitmq.port=${rabbit.amqpPort}",
            "spring.rabbitmq.username=${rabbit.adminUsername}", "spring.rabbitmq.password=${rabbit.adminPassword}",
            "spring.kafka.bootstrap-servers=${kafka.bootstrapServers}",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        )

    @Test
    fun `the request id reaches a RabbitMQ listener and a Kafka listener`() {
        seenRabbit = CompletableFuture()
        seenKafka = CompletableFuture()
        runner().run { ctx ->
            assertThat(ctx).hasNotFailed()
            MDC.put(CorrelationIds.MDC_KEY, "request-from-app-0001")
            ctx.getBean(RabbitTemplate::class.java).convertAndSend("cid-test", "hello")
            @Suppress("UNCHECKED_CAST")
            (ctx.getBean(KafkaTemplate::class.java) as KafkaTemplate<String, String>).send("cid-test", "k", "hello").get(30, TimeUnit.SECONDS)
            MDC.clear()

            assertThat(seenRabbit.get(30, TimeUnit.SECONDS).id).isEqualTo("request-from-app-0001")
            assertThat(seenKafka.get(30, TimeUnit.SECONDS).id).isEqualTo("request-from-app-0001")
        }
    }

    @Test
    fun `a message sent with no id still gets one in the listener`() {
        seenRabbit = CompletableFuture()
        seenKafka = CompletableFuture()
        runner().run { ctx ->
            assertThat(ctx).hasNotFailed()
            ctx.getBean(RabbitTemplate::class.java).convertAndSend("cid-test", "hello")
            @Suppress("UNCHECKED_CAST")
            (ctx.getBean(KafkaTemplate::class.java) as KafkaTemplate<String, String>).send("cid-test", "k", "hello").get(30, TimeUnit.SECONDS)

            assertThat(CorrelationIds.isValid(seenRabbit.get(30, TimeUnit.SECONDS).id)).isTrue()
            assertThat(CorrelationIds.isValid(seenKafka.get(30, TimeUnit.SECONDS).id)).isTrue()
        }
    }
}
