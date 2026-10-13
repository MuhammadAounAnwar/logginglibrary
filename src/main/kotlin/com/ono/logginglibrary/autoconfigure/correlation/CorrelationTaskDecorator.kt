package com.ono.logginglibrary.autoconfigure.correlation

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator

/**
 * Copies the MDC of the submitting thread (the correlation id and anything else a filter put there) onto the pool thread for the
 * length of the task, then restores what the pool thread had. Spring Boot applies a single [TaskDecorator] bean to its
 * `applicationTaskExecutor`, which is what `@Async` uses.
 */
class CorrelationTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val captured = MDC.getCopyOfContextMap()
        return Runnable {
            val previous = MDC.getCopyOfContextMap()
            if (captured != null) MDC.setContextMap(captured) else MDC.clear()
            try {
                runnable.run()
            } finally {
                if (previous != null) MDC.setContextMap(previous) else MDC.clear()
            }
        }
    }
}
