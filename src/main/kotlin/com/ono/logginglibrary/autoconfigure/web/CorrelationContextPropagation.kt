package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.core.correlation.CorrelationIds
import io.micrometer.context.ContextRegistry
import org.slf4j.MDC

/**
 * Teaches Reactor's automatic context propagation to copy the correlation id between the Reactor context and the SLF4J MDC.
 *
 * Without this accessor `Hooks.enableAutomaticContextPropagation()` has nothing to copy: the web filter writes the id to the
 * Reactor context, but `%X{correlationId}` in a log line stays empty, because nothing moves it into the MDC of the thread that
 * logs. (Found by following one request through the running gateway: its log lines carried no id.)
 */
object CorrelationContextPropagation {

    @Volatile private var registered = false

    @Synchronized
    fun register() {
        if (registered) return
        ContextRegistry.getInstance().registerThreadLocalAccessor(
            CorrelationIds.MDC_KEY,
            { MDC.get(CorrelationIds.MDC_KEY) },
            { value -> MDC.put(CorrelationIds.MDC_KEY, value) },
            { MDC.remove(CorrelationIds.MDC_KEY) },
        )
        registered = true
    }
}
