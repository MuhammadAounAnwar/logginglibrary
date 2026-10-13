package com.ono.logginglibrary.autoconfigure.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.jdbc.datasource.DelegatingDataSource
import java.sql.Connection
import java.util.Collections
import java.util.WeakHashMap
import javax.sql.DataSource

/**
 * Tells PostgreSQL which request a connection is working for, by setting the session variable `app.correlation_id` whenever a
 * connection is borrowed and the id has changed since the last time that physical connection was used.
 *
 * Why: the replication outbox is filled by a database trigger, which cannot see the application's MDC. The trigger reads
 * `current_setting('app.correlation_id', true)` into the outbox row, and the publisher puts it on the Kafka record, so the change a
 * request caused can be traced from the request to the record to the consumer. A connection used by a job with no id is cleared.
 *
 * Cost: one extra statement when the id changes (a new request on that connection), none otherwise. Off unless
 * `ono.observability.correlation.database-session=true`. Does nothing on a database that is not PostgreSQL (the H2 of the tests).
 */
class CorrelationAwareDataSource(delegate: DataSource) : DelegatingDataSource(delegate) {

    private val log = LoggerFactory.getLogger(CorrelationAwareDataSource::class.java)
    private val lastSet: MutableMap<Connection, String> = Collections.synchronizedMap(WeakHashMap())

    @Volatile private var postgres: Boolean? = null

    override fun getConnection(): Connection = tag(super.getConnection())

    override fun getConnection(username: String, password: String): Connection = tag(super.getConnection(username, password))

    private fun tag(connection: Connection): Connection {
        try {
            if (postgres == null) postgres = connection.metaData.databaseProductName.equals("PostgreSQL", ignoreCase = true)
            if (postgres != true) return connection
            val physical = runCatching { connection.unwrap(Connection::class.java) }.getOrDefault(connection)
            val wanted = CorrelationIds.current().orEmpty()
            if (lastSet[physical] == wanted || (wanted.isEmpty() && lastSet[physical] == null)) return connection
            connection.prepareStatement("SELECT set_config('app.correlation_id', ?, false)").use {
                it.setString(1, wanted)
                it.execute()
            }
            lastSet[physical] = wanted
        } catch (e: Exception) {
            // Never fail a request because the correlation label could not be written.
            log.debug("Could not set app.correlation_id: {}", e.message)
        }
        return connection
    }
}

/** Wraps every [DataSource] bean in a [CorrelationAwareDataSource] (once). */
class CorrelationDataSourcePostProcessor : BeanPostProcessor {
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
        if (bean is DataSource && bean !is CorrelationAwareDataSource) CorrelationAwareDataSource(bean) else bean
}
