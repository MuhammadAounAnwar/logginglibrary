package com.ono.logginglibrary.mdc

import com.ono.logginglibrary.core.mdc.CorrelationIdGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CorrelationIdGeneratorTest {

    @Test
    fun `DEFAULT generates valid UUID strings`() {
        val id = CorrelationIdGenerator.DEFAULT.generate()
        assertThat(id).isNotBlank()
        // Should be a valid UUID format
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    @Test
    fun `DEFAULT generates unique IDs`() {
        val ids = (1..100).map { CorrelationIdGenerator.DEFAULT.generate() }.toSet()
        assertThat(ids).hasSize(100)
    }

    @Test
    fun `custom generator is used`() {
        var counter = 0
        val custom = CorrelationIdGenerator { "req-${++counter}" }

        assertThat(custom.generate()).isEqualTo("req-1")
        assertThat(custom.generate()).isEqualTo("req-2")
    }
}
