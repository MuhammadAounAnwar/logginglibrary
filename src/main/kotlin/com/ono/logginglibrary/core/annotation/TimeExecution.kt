package com.ono.logginglibrary.core.annotation


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class TimedExecution(
    val metricName: String = "method.execution",
    val description: String = ""
)