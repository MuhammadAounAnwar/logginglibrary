package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.correlation.CorrelationAwareDataSource
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationKafkaPostProcessor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationMessagePostProcessor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationProducerInterceptor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationRabbitFactoryPostProcessor
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationRabbitListenerAdvice
import com.ono.logginglibrary.autoconfigure.correlation.CorrelationRecordInterceptor
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.slf4j.MDC
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.aop.framework.ProxyFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import javax.sql.DataSource

class CorrelationMessagingTest {

    @AfterEach
    fun clean() = MDC.clear()

    // ── RabbitMQ ────────────────────────────────────────────────────────────────

    @Test
    fun `a published message carries the current id, and keeps an id it already has`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        val plain = CorrelationMessagePostProcessor().postProcessMessage(Message(ByteArray(0), MessageProperties()))
        assertThat(plain.messageProperties.getHeader<String>(CorrelationIds.HEADER)).isEqualTo("request-0001")

        val own = Message(ByteArray(0), MessageProperties().apply { setHeader(CorrelationIds.HEADER, "explicit-0001") })
        assertThat(CorrelationMessagePostProcessor().postProcessMessage(own).messageProperties.getHeader<String>(CorrelationIds.HEADER)).isEqualTo("explicit-0001")
    }

    @Test
    fun `a message published with no current id gets no header`() {
        val msg = CorrelationMessagePostProcessor().postProcessMessage(Message(ByteArray(0), MessageProperties()))
        assertThat(msg.messageProperties.headers).doesNotContainKey(CorrelationIds.HEADER)
    }

    private interface Listener { fun onMessage(message: Message): String? }

    private fun adviced(inside: () -> String?): Listener {
        val target = object : Listener { override fun onMessage(message: Message): String? = inside() }
        return ProxyFactory(target).apply { addAdvice(CorrelationRabbitListenerAdvice(generator = CorrelationIdGenerator { "generated-0001" })) }.proxy as Listener
    }

    @Test
    fun `a listener sees the id of the message it handles and the thread is restored afterwards`() {
        MDC.put(CorrelationIds.MDC_KEY, "thread-own-0001")
        val listener = adviced { MDC.get(CorrelationIds.MDC_KEY) }
        val msg = Message(ByteArray(0), MessageProperties().apply { setHeader(CorrelationIds.HEADER, "from-message-1") })

        assertThat(listener.onMessage(msg)).isEqualTo("from-message-1")
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("thread-own-0001")
    }

    @Test
    fun `a listener gets a fresh id when the sender sent none or sent a bad one`() {
        val listener = adviced { MDC.get(CorrelationIds.MDC_KEY) }
        assertThat(listener.onMessage(Message(ByteArray(0), MessageProperties()))).isEqualTo("generated-0001")
        val bad = Message(ByteArray(0), MessageProperties().apply { setHeader(CorrelationIds.HEADER, "bad id\nx") })
        assertThat(listener.onMessage(bad)).isEqualTo("generated-0001")
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull()
    }

    @Test
    fun `the advice is added once to a listener container factory`() {
        val factory = SimpleRabbitListenerContainerFactory()
        val processor = CorrelationRabbitFactoryPostProcessor(CorrelationRabbitListenerAdvice())

        processor.postProcessBeforeInitialization(factory, "f")
        processor.postProcessBeforeInitialization(factory, "f")

        assertThat(factory.adviceChain!!.filterIsInstance<CorrelationRabbitListenerAdvice>()).hasSize(1)
    }

    // ── Kafka ───────────────────────────────────────────────────────────────────

    @Test
    fun `a record carries the current id, and keeps a header it already has`() {
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        val interceptor = CorrelationProducerInterceptor<String, String>()

        val plain = interceptor.onSend(ProducerRecord("t", "k", "v"))
        assertThat(String(plain.headers().lastHeader(CorrelationIds.HEADER).value())).isEqualTo("request-0001")

        val own = ProducerRecord<String, String>("t", "k", "v").also { it.headers().add(CorrelationIds.HEADER, "from-outbox-01".toByteArray()) }
        assertThat(String(interceptor.onSend(own).headers().lastHeader(CorrelationIds.HEADER).value())).isEqualTo("from-outbox-01")
        assertThat(own.headers().headers(CorrelationIds.HEADER).count()).isEqualTo(1)
    }

    @Test
    fun `a record sent with no current id gets no header`() {
        val sent = CorrelationProducerInterceptor<String, String>().onSend(ProducerRecord("t", "k", "v"))
        assertThat(sent.headers().lastHeader(CorrelationIds.HEADER)).isNull()
    }

    @Test
    fun `every KafkaTemplate in the context sends the id as a header`() {
        val producer = MockProducer(true, StringSerializer(), StringSerializer())
        val factory = object : DefaultKafkaProducerFactory<String, String>(emptyMap()) {
            override fun createProducer(): org.apache.kafka.clients.producer.Producer<String, String> = producer
        }
        val template = KafkaTemplate(factory)
        CorrelationKafkaPostProcessor().postProcessBeforeInitialization(template, "t")
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")

        runCatching { template.send("topic", "k", "v").get() }

        val sent = producer.history().single()
        assertThat(String(sent.headers().lastHeader(CorrelationIds.HEADER).value())).isEqualTo("request-0001")
    }

    private fun record(header: String?): ConsumerRecord<String, String> =
        ConsumerRecord<String, String>("t", 0, 0L, "k", "v").also { r -> header?.let { r.headers().add(CorrelationIds.HEADER, it.toByteArray()) } }

    @Test
    fun `a consumed record's id is the thread's id while it is handled, then the thread is restored`() {
        MDC.put(CorrelationIds.MDC_KEY, "thread-own-0001")
        val interceptor = CorrelationRecordInterceptor<String, String>(generator = CorrelationIdGenerator { "generated-0001" })
        val record = record("from-record-01")

        interceptor.intercept(record, mock(org.apache.kafka.clients.consumer.Consumer::class.java) as org.apache.kafka.clients.consumer.Consumer<String, String>)
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("from-record-01")
        interceptor.afterRecord(record, mock(org.apache.kafka.clients.consumer.Consumer::class.java) as org.apache.kafka.clients.consumer.Consumer<String, String>)

        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("thread-own-0001")
    }

    @Test
    fun `a consumed record with no header or a bad one still gets an id`() {
        val interceptor = CorrelationRecordInterceptor<String, String>(generator = CorrelationIdGenerator { "generated-0001" })
        val consumer = mock(org.apache.kafka.clients.consumer.Consumer::class.java) as org.apache.kafka.clients.consumer.Consumer<String, String>

        interceptor.intercept(record(null), consumer)
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("generated-0001")
        interceptor.afterRecord(record(null), consumer)
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull()

        interceptor.intercept(record("bad id"), consumer)
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("generated-0001")
    }

    // ── database session ────────────────────────────────────────────────────────

    private class Fixture(product: String) {
        val connection: Connection = mock(Connection::class.java)
        val statement: PreparedStatement = mock(PreparedStatement::class.java)
        val dataSource: DataSource = mock(DataSource::class.java)
        init {
            val meta = mock(DatabaseMetaData::class.java)
            `when`(meta.databaseProductName).thenReturn(product)
            `when`(connection.metaData).thenReturn(meta)
            `when`(connection.autoCommit).thenReturn(true)
            `when`(connection.unwrap(Connection::class.java)).thenReturn(connection)
            `when`(connection.prepareStatement("SELECT set_config('app.correlation_id', ?, false)")).thenReturn(statement)
            `when`(dataSource.connection).thenReturn(connection)
        }
    }

    @Test
    fun `a PostgreSQL connection is told the id once, and again only when the id changes`() {
        val f = Fixture("PostgreSQL")
        val ds = CorrelationAwareDataSource(f.dataSource)

        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        ds.connection; ds.connection
        verify(f.statement, times(1)).setString(1, "request-0001")

        MDC.put(CorrelationIds.MDC_KEY, "request-0002")
        ds.connection
        verify(f.statement, times(1)).setString(1, "request-0002")
        verify(f.statement, times(2)).execute()
    }

    @Test
    fun `a connection that was tagged is cleared when the next work has no id`() {
        val f = Fixture("PostgreSQL")
        val ds = CorrelationAwareDataSource(f.dataSource)
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        ds.connection
        MDC.clear()

        ds.connection

        verify(f.statement).setString(1, "")
    }

    @Test
    fun `a connection that never had an id and has none now costs nothing`() {
        val f = Fixture("PostgreSQL")
        CorrelationAwareDataSource(f.dataSource).connection
        verify(f.connection, never()).prepareStatement("SELECT set_config('app.correlation_id', ?, false)")
    }

    @Test
    fun `a connection inside a transaction is left alone`() {
        val f = Fixture("PostgreSQL")
        `when`(f.connection.autoCommit).thenReturn(false)
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        CorrelationAwareDataSource(f.dataSource).connection
        verify(f.connection, never()).prepareStatement("SELECT set_config('app.correlation_id', ?, false)")
    }

    @Test
    fun `a service's own Kafka interceptors are kept, ours runs first`() {
        val factory = org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, String>()
        var ran = false
        factory.setRecordInterceptor(org.springframework.kafka.listener.RecordInterceptor<String, String> { r, _ -> ran = true; r })
        CorrelationKafkaPostProcessor().postProcessBeforeInitialization(factory, "f")
        val interceptor = org.springframework.beans.DirectFieldAccessor(factory).getPropertyValue("recordInterceptor")
        assertThat(interceptor).isInstanceOf(org.springframework.kafka.listener.CompositeRecordInterceptor::class.java)
        @Suppress("UNCHECKED_CAST")
        (interceptor as org.springframework.kafka.listener.RecordInterceptor<String, String>)
            .intercept(record("from-record-01"), mock(org.apache.kafka.clients.consumer.Consumer::class.java) as org.apache.kafka.clients.consumer.Consumer<String, String>)
        assertThat(ran).isTrue()
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isEqualTo("from-record-01")
    }

    @Test
    fun `another database is left alone`() {
        val f = Fixture("H2")
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")
        CorrelationAwareDataSource(f.dataSource).connection
        verify(f.connection, never()).prepareStatement("SELECT set_config('app.correlation_id', ?, false)")
    }

    @Test
    fun `a failure to set the label never fails the request`() {
        val f = Fixture("PostgreSQL")
        `when`(f.statement.execute()).thenThrow(java.sql.SQLException("boom"))
        MDC.put(CorrelationIds.MDC_KEY, "request-0001")

        val connection = CorrelationAwareDataSource(f.dataSource).connection

        assertThat(connection).isSameAs(f.connection)
    }
}
