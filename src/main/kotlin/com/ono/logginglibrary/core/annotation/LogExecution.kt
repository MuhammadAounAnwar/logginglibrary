package com.ono.logginglibrary.core.annotation

/**
 * Logs method entry, exit, arguments, and results via AOP.
 *
 * @param logArguments whether to log method arguments (default: true)
 * @param logResult whether to log the return value (default: false)
 * @param level the SLF4J log level for entry/exit logs: "DEBUG", "INFO", "WARN", "TRACE" (default: "DEBUG").
 *              Slow-call warnings always log at WARN regardless of this setting.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class LogExecution(
    val logArguments: Boolean = true,
    val logResult: Boolean = false,
    val level: String = "DEBUG"
)
