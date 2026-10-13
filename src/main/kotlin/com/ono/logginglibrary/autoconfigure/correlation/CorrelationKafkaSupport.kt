package com.ono.logginglibrary.autoconfigure.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.correlation.CorrelationPropagation
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerInterceptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.MDC
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.RecordInterceptor

/**
 * Puts the correlation id on a Kafka record as a header. A record that already has the header keeps it (the replication
 * publisher sets the id it read from the outbox row, which is not the id of the publisher thread).
 */
class CorrelationProducerInterceptor<K, V>(private val header: String = CorrelationIds.HEADER) : ProducerInterceptor<K, V> {
    override fun onSend(record: ProducerRecord<K, V>): ProducerRecord<K, V> {
        if (record.headers().lastHeader(header) == null) {
            CorrelationIds.current()?.let { record.headers().add(header, it.toByteArray(Charsets.UTF_8)) }
        }
        return record
    }

    override fun onAcknowledgement(metadata: RecordMetadata?, exception: Exception?) = Unit
    override fun close() = Unit
    override fun configure(configs: MutableMap<String, *>?) = Unit
}

/**
 * Makes the header of each consumed record the thread's correlation id while the listener runs (a fresh id when the record has
 * none), and puts the previous value back afterwards.
 */
class CorrelationRecordInterceptor<K, V>(
    private val header: String = CorrelationIds.HEADER,
    private val generator: CorrelationIdGenerator = CorrelationIdGenerator.DEFAULT
) : RecordInterceptor<K, V> {

    private val previous = ThreadLocal<String?>()

    override fun intercept(record: ConsumerRecord<K, V>, consumer: Consumer<K, V>): ConsumerRecord<K, V> {
        val raw = record.headers().lastHeader(header)?.value()?.toString(Charsets.UTF_8)
        val id = CorrelationIds.resolve(raw, null, generator::generate)
        previous.set(MDC.get(CorrelationIds.MDC_KEY))
        MDC.put(CorrelationIds.MDC_KEY, id)
        return record
    }

    override fun afterRecord(record: ConsumerRecord<K, V>, consumer: Consumer<K, V>) {
        val before = previous.get()
        previous.remove()
        if (before != null) MDC.put(CorrelationIds.MDC_KEY, before) else MDC.remove(CorrelationIds.MDC_KEY)
    }
}

/** Gives every `KafkaTemplate` the producer interceptor and every `@KafkaListener` container factory the record interceptor. */
class CorrelationKafkaPostProcessor : BeanPostProcessor {
    @Suppress("UNCHECKED_CAST")
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        when (bean) {
            is KafkaTemplate<*, *> -> (bean as KafkaTemplate<Any, Any>).setProducerInterceptor(CorrelationProducerInterceptor())
            is AbstractKafkaListenerContainerFactory<*, *, *> ->
                (bean as AbstractKafkaListenerContainerFactory<*, Any, Any>).setRecordInterceptor(CorrelationRecordInterceptor())
        }
        return bean
    }
}

/** Runs [block] with the record's correlation id as the thread's id; for code that handles a record outside a listener container. */
inline fun <K, V, T> ConsumerRecord<K, V>.withCorrelation(header: String = CorrelationIds.HEADER, block: () -> T): T =
    CorrelationPropagation.withId(headers().lastHeader(header)?.value()?.toString(Charsets.UTF_8)?.let { CorrelationIds.sanitize(it) }, block)
