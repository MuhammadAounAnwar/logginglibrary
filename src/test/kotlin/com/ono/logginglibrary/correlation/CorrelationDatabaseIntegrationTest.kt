package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.correlation.CorrelationAwareDataSource
import com.ono.logginglibrary.core.correlation.CorrelationIds
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

/** The database session label on a real PostgreSQL behind a real connection pool, and what it costs. */
@Testcontainers(disabledWithoutDocker = true)
class CorrelationDatabaseIntegrationTest {

    companion object {
        @Container @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    }

    @AfterEach
    fun clean() = MDC.clear()

    private fun pool(size: Int): HikariDataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = postgres.jdbcUrl; username = postgres.username; password = postgres.password; maximumPoolSize = size
    })

    private fun label(ds: DataSource): String? = ds.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT NULLIF(current_setting('app.correlation_id', true), '')").use { r -> r.next(); r.getString(1) } }
    }

    @Test
    fun `a connection checked out for a request can read that request's id in SQL`() {
        pool(1).use { hikari ->
            val ds = CorrelationAwareDataSource(hikari)
            MDC.put(CorrelationIds.MDC_KEY, "request-from-app-0001")
            assertThat(label(ds)).isEqualTo("request-from-app-0001")
        }
    }

    @Test
    fun `the same pooled connection never shows the previous request's id`() {
        pool(1).use { hikari ->
            val ds = CorrelationAwareDataSource(hikari)
            MDC.put(CorrelationIds.MDC_KEY, "request-from-app-0001")
            assertThat(label(ds)).isEqualTo("request-from-app-0001")

            MDC.put(CorrelationIds.MDC_KEY, "request-from-app-0002")
            assertThat(label(ds)).isEqualTo("request-from-app-0002")

            MDC.clear()
            assertThat(label(ds)).isNull()
        }
    }

    @Test
    fun `a trigger sees the id, the way the replication outbox uses it`() {
        pool(1).use { hikari ->
            hikari.connection.use { c ->
                c.createStatement().use {
                    it.execute("CREATE TABLE IF NOT EXISTS outbox_probe (cid TEXT)")
                    it.execute("CREATE TABLE IF NOT EXISTS thing (id INT)")
                    it.execute(
                        """CREATE OR REPLACE FUNCTION probe() RETURNS trigger AS ${'$'}${'$'}
                           BEGIN INSERT INTO outbox_probe VALUES (NULLIF(current_setting('app.correlation_id', true), '')); RETURN NULL; END
                           ${'$'}${'$'} LANGUAGE plpgsql""",
                    )
                    it.execute("DROP TRIGGER IF EXISTS t ON thing")
                    it.execute("CREATE TRIGGER t AFTER INSERT ON thing FOR EACH ROW EXECUTE FUNCTION probe()")
                }
            }
            val ds = CorrelationAwareDataSource(hikari)
            MDC.put(CorrelationIds.MDC_KEY, "request-from-app-0001")
            ds.connection.use { it.createStatement().use { s -> s.execute("INSERT INTO thing VALUES (1)") } }

            val seen = hikari.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT cid FROM outbox_probe").use { r -> r.next(); r.getString(1) } } }
            assertThat(seen).isEqualTo("request-from-app-0001")
        }
    }

    @Test
    fun `what the hook costs per checkout is small`() {
        pool(4).use { hikari ->
            val plain = hikari
            val aware = CorrelationAwareDataSource(hikari)
            val rounds = 2000
            fun time(ds: DataSource, newIdEachTime: Boolean): Double {
                repeat(200) { ds.connection.use { c -> c.createStatement().use { it.execute("SELECT 1") } } } // warm up
                val start = System.nanoTime()
                repeat(rounds) { i ->
                    if (newIdEachTime) MDC.put(CorrelationIds.MDC_KEY, "request-number-%08d".format(i))
                    ds.connection.use { c -> c.createStatement().use { it.execute("SELECT 1") } }
                }
                MDC.clear()
                return (System.nanoTime() - start) / 1e6 / rounds
            }
            val base = time(plain, false)
            val withLabel = time(aware, true)
            println("CORRELATION-DB-COST per checkout plain=%.3f ms, with a new id every time=%.3f ms, extra=%.3f ms".format(base, withLabel, withLabel - base))
            assertThat(withLabel - base).isLessThan(5.0)
        }
    }
}
