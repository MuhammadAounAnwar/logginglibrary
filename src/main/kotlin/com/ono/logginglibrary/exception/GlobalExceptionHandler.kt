package com.ono.logginglibrary.exception

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

@RestControllerAdvice
@ConditionalOnClass(RestControllerAdvice::class)
@ConditionalOnMissingBean(GlobalExceptionHandler::class)
@ConditionalOnProperty(
    prefix = "ono.logging.exceptions",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /* =========================
     * 1️⃣ Business Exceptions
     * ========================= */
    @ExceptionHandler(OnoBusinessException::class)
    fun handleBusinessException(
        ex: OnoBusinessException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        log.warn("Business exception: {}", ex.message)

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    message = ex.message,
                    path = request.getDescription(false)
                )
            )
    }

    /* =========================
     * 2️⃣ Token-related Errors
     * ========================= */
    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(
        ex: TokenExpiredException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        log.info("Token expired: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.GONE)
            .body(
                ErrorResponse(
                    status = HttpStatus.GONE.value(),
                    message = ex.message,
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(TokenNotFoundException::class)
    fun handleTokenNotFound(
        ex: TokenNotFoundException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        log.info("Token not found: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    status = HttpStatus.NOT_FOUND.value(),
                    message = ex.message,
                    path = request.getDescription(false)
                )
            )
    }

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(
        ex: InvalidTokenException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        log.info("Invalid token: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    status = HttpStatus.UNAUTHORIZED.value(),
                    message = ex.message,
                    path = request.getDescription(false)
                )
            )
    }

    /* =========================
     * 3️⃣ Validation Errors
     * ========================= */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        log.warn("Validation failed: {}", message)

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    status = HttpStatus.BAD_REQUEST.value(),
                    message = message,
                    path = request.getDescription(false)
                )
            )
    }

    /* =========================
     * 4️⃣ Fallback (LAST RESORT)
     * ========================= */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {

        log.error("Unhandled exception", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    message = "Internal server error",
                    path = request.getDescription(false)
                )
            )
    }
}
