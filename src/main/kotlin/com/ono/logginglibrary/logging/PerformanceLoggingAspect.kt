package com.ono.logginglibrary.logging

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class PerformanceLoggingAspect {

    private val log = LoggerFactory.getLogger(this.javaClass)

    // This Pointcut targets any class annotated with @Service
    // inside any sub-package of 'com.ono'
//    @Around("@annotation(com.ono.common.logging.LogExecutionTime)")  --- if want to log specific method
    @Around("within(@org.springframework.stereotype.Service *)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val start = System.currentTimeMillis()
        val className = joinPoint.signature.declaringTypeName
        val methodName = joinPoint.signature.name

        // Execute the actual service method
        val result = joinPoint.proceed()

        val executionTime = System.currentTimeMillis() - start

        // Log the results
        if (executionTime > 500) {
            log.warn("SLOW PERFORMANCE: {}.{} took {}ms", className, methodName, executionTime)
        } else {
            log.info("Execution: {}.{} took {}ms", className, methodName, executionTime)
        }

        return result
    }
}