package com.ono.logginglibrary.autoconfigure.exception

import com.ono.logginglibrary.core.exception.ErrorResponse
import com.ono.logginglibrary.core.exception.OnoBusinessException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant

@RestControllerAdvice
class DefaultGlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(DefaultGlobalExceptionHandler::class.java)

    @ExceptionHandler(OnoBusinessException::class)
    fun handleBusinessException(
        ex: OnoBusinessException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {

        log.warn("Business exception: {}", ex.message)

        return buildResponse(
            status = ex.httpStatus,
            message = ex.message,
            exchange = exchange
        )
    }

    @ExceptionHandler(org.springframework.web.bind.support.WebExchangeBindException::class)
    fun handleValidationErrors(
        ex: org.springframework.web.bind.support.WebExchangeBindException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {

        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        log.warn("Validation failed: {}", message)

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = message,
            exchange = exchange
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {

        log.error("Unhandled exception", ex)

        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            message = "Internal server error",
            exchange = exchange
        )
    }

    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {

        val path = exchange.request.uri.path
        val correlationId = exchange.request.headers.getFirst("X-Correlation-Id")

        val body = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = path,
            correlationId = correlationId,
            timestamp = Instant.now()
        )

        return Mono.just(ResponseEntity.status(status).body(body))
    }
}
