package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.core.ObservabilityConstants
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

/**
 * Fallback Servlet/MVC exception handler provided by logginglibrary.
 *
 * Registered at [Ordered.LOWEST_PRECEDENCE] so service-specific handlers always take priority.
 * Handles common exception types that services often omit and echoes [correlationHeader] in
 * every error response body for distributed tracing.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
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
        return buildResponse(ex.httpStatus, ex.message, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", message)
        return buildResponse(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request body: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", request)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Missing required parameter: {}", ex.parameterName)
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing required parameter: ${ex.parameterName}", request)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        ex: MissingRequestHeaderException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Missing required header: {}", ex.headerName)
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing required header: ${ex.headerName}", request)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Type mismatch for parameter '{}': {}", ex.name, ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter: ${ex.name}", request)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Access denied on {}: {}", request.requestURI, ex.message)
        return buildResponse(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Authentication failed on {}: {}", request.requestURI, ex.message)
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required", request)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(
        ex: NoSuchElementException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Resource not found: {}", ex.message)
        return buildResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found", request)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.warn("Invalid argument: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception on {}", request.requestURI, ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request)
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI,
            correlationId = request.getHeader(correlationHeader),
            timestamp = Instant.now()
        )
        return ResponseEntity.status(status).body(body)
    }
}
