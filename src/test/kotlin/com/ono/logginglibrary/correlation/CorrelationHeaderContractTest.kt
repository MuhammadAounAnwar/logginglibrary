package com.ono.logginglibrary.correlation

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.ObservabilityConstants
import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The header name is a contract between the app, the gateway and every service. The gateway repo pins the same two strings in its
 * own test; if either side changes one, one of the two tests fails instead of the correlation silently splitting in two.
 */
class CorrelationHeaderContractTest {

    @Test
    fun `the default configured header is the canonical one`() {
        assertThat(ObservabilityProperties().logging.correlationHeader).isEqualTo("X-Correlation-Id")
        assertThat(ObservabilityConstants.DEFAULT_CORRELATION_HEADER).isEqualTo(CorrelationIds.HEADER)
    }

    @Test
    fun `the alternate header is the one the gateway used before`() {
        assertThat(CorrelationIds.ALT_HEADER).isEqualTo("X-Request-ID")
    }
}
