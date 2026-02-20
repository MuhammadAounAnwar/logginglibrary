package com.ono.logginglibrary.autoconfigure.aop

import com.ono.logginglibrary.core.annotation.LogExecution
import com.ono.logginglibrary.core.annotation.TimedExecution
import com.ono.logginglibrary.core.logging.LoggingUtils
import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
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
    private val meterRegistry: MeterRegistry?
) {

    private val log = LoggingUtils.logger(ObservabilityAspect::class)

    @Around("@annotation(logExecution)")
    fun logExecution(
        joinPoint: ProceedingJoinPoint,
        logExecution: LogExecution
    ): Any? {
        val methodName = joinPoint.signature.name
        val start = System.currentTimeMillis()

        if (logExecution.logArguments && joinPoint.args.isNotEmpty()) {
            log.debug("[{}] called with args: {}", methodName, joinPoint.args)
        }

        return try {
            when (val result = joinPoint.proceed()) {
                is Mono<*> -> result
                    .doOnSuccess { value ->
                        log.debug("[{}] completed in {}ms", methodName, System.currentTimeMillis() - start)
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
                        log.debug("[{}] completed in {}ms", methodName, System.currentTimeMillis() - start)
                    }
                    .doOnError { ex ->
                        log.warn("[{}] failed after {}ms", methodName, System.currentTimeMillis() - start, ex)
                    }

                else -> {
                    log.debug("[{}] completed in {}ms", methodName, System.currentTimeMillis() - start)
                    if (logExecution.logResult) {
                        log.debug("[{}] result: {}", methodName, result)
                    }
                    result
                }
            }
        } catch (e: Exception) {
            log.warn("[{}] failed after {}ms", methodName, System.currentTimeMillis() - start, e)
            throw e
        }
    }

    @Around("@annotation(timedExecution)")
    fun timeExecution(
        joinPoint: ProceedingJoinPoint,
        timedExecution: TimedExecution
    ): Any? {

        val start = System.nanoTime()
        val result = joinPoint.proceed()

        return when (result) {

            is Mono<*> ->
                result.doFinally {
                    record(timedExecution.metricName, start)
                }

            is Flux<*> ->
                result.doFinally {
                    record(timedExecution.metricName, start)
                }

            else -> {
                record(timedExecution.metricName, start)
                result
            }
        }
    }

    private fun record(metric: String, start: Long) {
        meterRegistry?.timer(metric)
            ?.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}