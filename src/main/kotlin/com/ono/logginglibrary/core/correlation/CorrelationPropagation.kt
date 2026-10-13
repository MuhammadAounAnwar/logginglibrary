package com.ono.logginglibrary.core.correlation

import org.slf4j.MDC
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * Helpers for code that hands work to another thread. The correlation id lives in the SLF4J MDC of the request thread; a pool
 * thread starts without it, so its log lines and any call it makes would carry no id.
 */
object CorrelationPropagation {

    /** Runs [block] with [id] as the thread's correlation id, and puts back what was there before. A null id leaves the thread alone. */
    inline fun <T> withId(id: String?, block: () -> T): T {
        if (id == null) return block()
        val previous = MDC.get(CorrelationIds.MDC_KEY)
        MDC.put(CorrelationIds.MDC_KEY, id)
        try {
            return block()
        } finally {
            if (previous != null) MDC.put(CorrelationIds.MDC_KEY, previous) else MDC.remove(CorrelationIds.MDC_KEY)
        }
    }

    /** [task] that runs with the id of the thread that called this, wherever it ends up running. */
    fun wrap(task: Runnable): Runnable {
        val id = CorrelationIds.current()
        return Runnable { withId(id) { task.run() } }
    }

    fun <T> wrap(task: Callable<T>): Callable<T> {
        val id = CorrelationIds.current()
        return Callable { withId(id) { task.call() } }
    }

    /** [executor] whose tasks keep the correlation id of whoever submitted them. */
    fun wrap(executor: Executor): Executor = Executor { task -> executor.execute(wrap(task)) }
}

/** `executor.execute(task.withCorrelation())` for a task that must keep the caller's correlation id. */
fun Runnable.withCorrelation(): Runnable = CorrelationPropagation.wrap(this)

fun <T> Callable<T>.withCorrelation(): Callable<T> = CorrelationPropagation.wrap(this)
