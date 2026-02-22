package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.core.ObservabilityConstants
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * Global exception handler for Servlet/MVC applications.
 * Mirrors [DefaultGlobalExceptionHandler] but uses HttpServletRequest instead of ServerWebExchange.
 */
@RestControllerAdvice
class DefaultServletExceptionHandler(
    private val correlationHeader: String = ObservabilityConstants.DEFAULT_CORRELATION_HEADER
) {

    private val log = LoggerFactory.getLogger(DefaultServletExceptionHandler::class.java)

    @ExceptionHandler(OnoBusinessException::class)
    fun handleBusinessException(
        ex: OnoBusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Business exception: {}", ex.message)
        return buildResponse(
            status = ex.httpStatus,
            message = ex.message,
            request = request
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        log.warn("Validation failed: {}", message)

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = message,
            request = request
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            message = "Internal server error",
            request = request
        )
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val path = request.requestURI
        val correlationId = request.getHeader(correlationHeader)

        val body = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = path,
            correlationId = correlationId,
            timestamp = Instant.now()
        )

        return ResponseEntity.status(status).body(body)
    }
}
