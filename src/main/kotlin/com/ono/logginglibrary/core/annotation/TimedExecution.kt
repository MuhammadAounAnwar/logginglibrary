package com.ono.logginglibrary.core.annotation

import com.ono.logginglibrary.core.ObservabilityConstants

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class TimedExecution(
    val metricName: String = ObservabilityConstants.DEFAULT_METHOD_EXECUTION_TIMER,
    val description: String = ""
)
