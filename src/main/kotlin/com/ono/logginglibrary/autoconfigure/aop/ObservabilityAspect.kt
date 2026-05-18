package com.ono.logginglibrary.autoconfigure.aop

import com.ono.logginglibrary.autoconfigure.ObservabilityProperties
import com.ono.logginglibrary.core.annotation.LogExecution
import com.ono.logginglibrary.core.annotation.SkipLogging
import com.ono.logginglibrary.core.annotation.TimedExecution
import com.ono.logginglibrary.core.logging.LoggingUtils
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

@Aspect
@Configuration
@ConditionalOnClass(Aspect::class)
@ConditionalOnProperty(
    prefix = "ono.observability.aop",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class ObservabilityAspect(
    private val meterRegistry: MeterRegistry?,
    private val properties: ObservabilityProperties
) {

    private val log = LoggingUtils.logger(ObservabilityAspect::class)

    @Around("@annotation(logExecution)")
    fun logExecution(
        joinPoint: ProceedingJoinPoint,
        logExecution: LogExecution
    ): Any? {
        if (joinPoint.isSkipLogging()) return joinPoint.proceed()

        val methodName = joinPoint.signature.name
        val startNs = System.nanoTime()

        if (logExecution.logArguments && joinPoint.args.isNotEmpty()) {
            logAtLevel(log, logExecution.level, "[{}] called with args: {}", methodName, joinPoint.args)
        }

        return try {
            when (val result = joinPoint.proceed()) {
                is Mono<*> -> result
                    .doOnSuccess { value ->
                        logCompletion(methodName, elapsedMs(startNs), logExecution.level)
                        if (logExecution.logResult && value != null) {
                            logAtLevel(log, logExecution.level, "[{}] result: {}", methodName, value)
                        }
                    }
                    .doOnError { ex ->
                        log.warn("[{}] failed after {}ms", methodName, elapsedMs(startNs), ex)
                    }

                is Flux<*> -> result
                    .doOnNext { value ->
                        if (logExecution.logResult) {
                            logAtLevel(log, logExecution.level, "[{}] emitted: {}", methodName, value)
                        }
                    }
                    .doOnComplete {
                        logCompletion(methodName, elapsedMs(startNs), logExecution.level)
                    }
                    .doOnError { ex ->
                        log.warn("[{}] failed after {}ms", methodName, elapsedMs(startNs), ex)
                    }

                else -> {
                    logCompletion(methodName, elapsedMs(startNs), logExecution.level)
                    if (logExecution.logResult) {
                        logAtLevel(log, logExecution.level, "[{}] result: {}", methodName, result)
                    }
                    result
                }
            }
        } catch (e: Throwable) {
            log.warn("[{}] failed after {}ms", methodName, elapsedMs(startNs), e)
            throw e
        }
    }

    @Around("@annotation(timedExecution)")
    fun timeExecution(
        joinPoint: ProceedingJoinPoint,
        timedExecution: TimedExecution
    ): Any? {
        if (joinPoint.isSkipLogging()) return joinPoint.proceed()

        val start = System.nanoTime()

        val result = try {
            joinPoint.proceed()
        } catch (e: Throwable) {
            record(timedExecution.metricName, timedExecution.description, start)
            throw e
        }

        return when (result) {
            is Mono<*> -> result.doFinally { record(timedExecution.metricName, timedExecution.description, start) }
            is Flux<*> -> result.doFinally { record(timedExecution.metricName, timedExecution.description, start) }
            else -> {
                record(timedExecution.metricName, timedExecution.description, start)
                result
            }
        }
    }

    private fun logCompletion(methodName: String, elapsedMs: Long, level: String) {
        if (elapsedMs > properties.aop.slowThresholdMs) {
            log.warn(
                "[{}] slow execution: {}ms (threshold: {}ms)",
                methodName, elapsedMs, properties.aop.slowThresholdMs
            )
        } else {
            logAtLevel(log, level, "[{}] completed in {}ms", methodName, elapsedMs)
        }
    }

    private fun record(metricName: String, metricDescription: String, start: Long) {
        meterRegistry?.let { registry ->
            val timer = Timer.builder(metricName)
                .apply { if (metricDescription.isNotBlank()) description(metricDescription) }
                .register(registry)
            timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    companion object {
        private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000L

        private fun logAtLevel(logger: Logger, level: String, format: String, vararg args: Any?) {
            when (level.uppercase()) {
                "TRACE" -> logger.trace(format, *args)
                "DEBUG" -> logger.debug(format, *args)
                "INFO" -> logger.info(format, *args)
                "WARN" -> logger.warn(format, *args)
                "ERROR" -> logger.error(format, *args)
                else -> logger.debug(format, *args)
            }
        }
    }
}

private fun ProceedingJoinPoint.isSkipLogging(): Boolean {
    val method = (signature as MethodSignature).method
    return method.isAnnotationPresent(SkipLogging::class.java) ||
        signature.declaringType.isAnnotationPresent(SkipLogging::class.java)
}
