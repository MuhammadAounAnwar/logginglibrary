package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class CorrelationIdsTest {

    @Test
    fun `ordinary ids are valid`() {
        listOf("3f9a1c2b-7d44-4e0a-9b21-5c1d8e6f0a11", "req_12345678", "abc.DEF-123_xyz", "a".repeat(64), "12345678")
            .forEach { assertThat(CorrelationIds.isValid(it)).describedAs(it).isTrue() }
    }

    @Test
    fun `anything that could damage a log line or a query is not valid`() {
        listOf(
            null, "", "short", "a".repeat(65), "has space in it", "line\nbreak-1234", "tab\there1234", "json{\"a\":1}12",
            "semi;colon1234", "<script>12345", "../../etc/pass", "non-ascii-é-1234", "x".repeat(10_000),
        ).forEach { assertThat(CorrelationIds.isValid(it)).describedAs(it).isFalse() }
    }

    @Test
    fun `sanitize trims whitespace around a good id and drops a bad one`() {
        assertThat(CorrelationIds.sanitize("  abcd1234  ")).isEqualTo("abcd1234")
        assertThat(CorrelationIds.sanitize("bad id")).isNull()
        assertThat(CorrelationIds.sanitize(null)).isNull()
    }

    @Test
    fun `resolve prefers the canonical header, then the alternate, then a generated id`() {
        assertThat(CorrelationIds.resolve("canonical-0001", "alternate-0001") { "generated-0001" }).isEqualTo("canonical-0001")
        assertThat(CorrelationIds.resolve(null, "alternate-0001") { "generated-0001" }).isEqualTo("alternate-0001")
        assertThat(CorrelationIds.resolve(null, null) { "generated-0001" }).isEqualTo("generated-0001")
    }

    @Test
    fun `a bad canonical id does not block a good alternate`() {
        assertThat(CorrelationIds.resolve("bad", "alternate-0001") { "generated-0001" }).isEqualTo("alternate-0001")
        assertThat(CorrelationIds.resolve("bad", "worse") { "generated-0001" }).isEqualTo("generated-0001")
    }

    @Test
    fun `current reads the MDC and ignores blanks`() {
        MDC.remove(CorrelationIds.MDC_KEY)
        assertThat(CorrelationIds.current()).isNull()
        MDC.put(CorrelationIds.MDC_KEY, " ")
        assertThat(CorrelationIds.current()).isNull()
        MDC.put(CorrelationIds.MDC_KEY, "abcd1234")
        assertThat(CorrelationIds.current()).isEqualTo("abcd1234")
        MDC.remove(CorrelationIds.MDC_KEY)
    }

    @Test
    fun `header names are the agreed ones`() {
        assertThat(CorrelationIds.HEADER).isEqualTo("X-Correlation-Id")
        assertThat(CorrelationIds.ALT_HEADER).isEqualTo("X-Request-ID")
        assertThat(CorrelationIds.MDC_KEY).isEqualTo("correlationId")
    }
}
