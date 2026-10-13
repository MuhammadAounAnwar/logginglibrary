package com.ono.logginglibrary.core.correlation

import com.ono.logginglibrary.core.ObservabilityConstants
import org.slf4j.MDC

/**
 * The one set of rules for the correlation id, shared by every filter, handler, client and messaging hook.
 *
 * - The canonical header is [HEADER] (`X-Correlation-Id`). [ALT_HEADER] (`X-Request-ID`) is accepted when the canonical one is
 *   absent, because the app, load balancers and older callers use that name.
 * - An id from a caller is kept only when it looks like an id ([isValid]): 8 to 64 characters of letters, digits, dot,
 *   underscore and dash. Anything else is replaced, so a caller cannot put newlines, JSON or a megabyte into our logs.
 * - The id is a label for finding log lines. It carries no authority and must never be used to decide who may do what.
 */
object CorrelationIds {

    const val HEADER = ObservabilityConstants.DEFAULT_CORRELATION_HEADER
    const val ALT_HEADER = "X-Request-ID"

    /** The SLF4J MDC key that `%X{correlationId}` in the log pattern reads. */
    const val MDC_KEY = ObservabilityConstants.DEFAULT_CORRELATION_ID_KEY

    /** The request attribute (servlet) or exchange attribute (WebFlux) that holds the id used for this request. */
    const val ATTRIBUTE = "ono.correlationId"

    private val VALID = Regex("^[A-Za-z0-9._-]{8,64}$")

    fun isValid(candidate: String?): Boolean = candidate != null && VALID.matches(candidate)

    /** The trimmed candidate when it is a valid id, else null. */
    fun sanitize(candidate: String?): String? = candidate?.trim()?.takeIf { isValid(it) }

    /** Canonical header first, then the alternate; a fresh id from [generate] when neither is a valid id. */
    fun resolve(primary: String?, alternate: String?, generate: () -> String): String =
        sanitize(primary) ?: sanitize(alternate) ?: generate()

    /** The id of the request being handled on this thread, if a filter or a messaging hook has set one. */
    fun current(): String? = MDC.get(MDC_KEY)?.takeIf { it.isNotBlank() }
}
