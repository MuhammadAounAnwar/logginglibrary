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
        val start = System.currentTimeMillis()

        if (logExecution.logArguments && joinPoint.args.isNotEmpty()) {
            log.debug("[{}] called with args: {}", methodName, joinPoint.args)
        }

        return try {
            when (val result = joinPoint.proceed()) {
                is Mono<*> -> result
                    .doOnSuccess { value ->
                        logCompletion(methodName, System.currentTimeMillis() - start)
                        if (logExecution.logResult && value != null) {
                            log.debug("[{}] result: {}", methodName, value)
                        }
                    }
                    .doOnError { ex ->
                        log.warn("[{}] failed after {}ms", methodName, System.currentTimeMillis() - start, ex)
                    }

                is Flux<*> -> result
                    .doOnNext { value ->
                        if (logExecution.logResult) {
                            log.debug("[{}] emitted: {}", methodName, value)
                        }
                    }
                    .doOnComplete {
                        logCompletion(methodName, System.currentTimeMillis() - start)
                    }
                    .doOnError { ex ->
                        log.warn("[{}] failed after {}ms", methodName, System.currentTimeMillis() - start, ex)
                    }

                else -> {
                    logCompletion(methodName, System.currentTimeMillis() - start)
                    if (logExecution.logResult) {
                        log.debug("[{}] result: {}", methodName, result)
                    }
                    result
                }
            }
        } catch (e: Throwable) {
            log.warn("[{}] failed after {}ms", methodName, System.currentTimeMillis() - start, e)
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

    private fun logCompletion(methodName: String, elapsedMs: Long) {
        if (elapsedMs > properties.aop.slowThresholdMs) {
            log.warn(
                "[{}] slow execution: {}ms (threshold: {}ms)",
                methodName, elapsedMs, properties.aop.slowThresholdMs
            )
        } else {
            log.debug("[{}] completed in {}ms", methodName, elapsedMs)
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
}

private fun ProceedingJoinPoint.isSkipLogging(): Boolean {
    val method = (signature as MethodSignature).method
    return method.isAnnotationPresent(SkipLogging::class.java) ||
        signature.declaringType.isAnnotationPresent(SkipLogging::class.java)
}
