package com.ono.logginglibrary.core.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class LogExecution(
    val logArguments: Boolean = true,
    val logResult: Boolean = false
)