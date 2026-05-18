package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.core.ObservabilityConstants
import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Fallback WebFlux exception handler provided by logginglibrary.
 *
 * Registered at [Ordered.LOWEST_PRECEDENCE] so service-specific handlers always take priority.
 * Handles common exception types that services often omit and echoes [correlationHeader] in
 * every error response body for distributed tracing.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
class DefaultGlobalExceptionHandler(
    private val correlationHeader: String = ObservabilityConstants.DEFAULT_CORRELATION_HEADER
) {

    private val log = LoggerFactory.getLogger(DefaultGlobalExceptionHandler::class.java)

    @ExceptionHandler(OnoBusinessException::class)
    fun handleBusinessException(
        ex: OnoBusinessException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.warn("Business exception: {}", ex.message)
        return buildResponse(ex.httpStatus, ex.message, exchange)
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationErrors(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", message)
        return buildResponse(HttpStatus.BAD_REQUEST, message, exchange)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        val status = HttpStatus.valueOf(ex.statusCode.value())
        val message = ex.reason ?: ex.message
        if (status.is5xxServerError) {
            log.error("Server error [{}]: {}", status, message, ex)
        } else {
            log.warn("Client error [{}]: {}", status, message)
        }
        return buildResponse(status, message, exchange)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.warn("Access denied on {}: {}", exchange.request.uri.path, ex.message)
        return buildResponse(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", exchange)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(
        ex: AuthenticationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.warn("Authentication failed on {}: {}", exchange.request.uri.path, ex.message)
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required", exchange)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(
        ex: NoSuchElementException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.warn("Resource not found: {}", ex.message)
        return buildResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found", exchange)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.warn("Invalid argument: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", exchange)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        log.error("Unhandled exception on {}", exchange.request.uri.path, ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", exchange)
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        val body = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = exchange.request.uri.path,
            correlationId = exchange.request.headers.getFirst(correlationHeader),
            timestamp = Instant.now()
        )
        return Mono.just(ResponseEntity.status(status).body(body))
    }
}
