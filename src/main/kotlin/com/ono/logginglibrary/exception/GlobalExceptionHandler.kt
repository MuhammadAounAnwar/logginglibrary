package com.ono.logginglibrary.exception

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
    matchIfMissing = false // IMPORTANT
)
class GlobalExceptionHandler {

    // 1. Handle Token Expired
    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(ex: TokenExpiredException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.GONE.value(), // 410 Gone is perfect for expired links
            message = ex.message,
            path = request.getDescription(false)
        )
        return ResponseEntity(error, HttpStatus.GONE)
    }

    // 2. Handle Token Not Found
    @ExceptionHandler(TokenNotFoundException::class)
    fun handleTokenNotFound(ex: TokenNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(), // 404
            message = ex.message,
            path = request.getDescription(false)
        )
        return ResponseEntity(error, HttpStatus.NOT_FOUND)
    }

    // 3. Handle Validation Errors (e.g., @Valid @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        val error = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            message = message,
            path = request.getDescription(false)
        )
        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
    }

    // 4. Catch-all for everything else (The Safety Net)
    @ExceptionHandler(Exception::class)
    fun handleGlobal(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = "An unexpected error occurred. Please try again later.",
            path = request.getDescription(false)
        )
        return ResponseEntity(error, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}