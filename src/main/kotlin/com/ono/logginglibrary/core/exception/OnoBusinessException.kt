package com.ono.logginglibrary.core.exception

import org.springframework.http.HttpStatus

sealed class OnoBusinessException(
    val errorCode: String,
    val httpStatus: HttpStatus,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NoSuchUserException(userId: String) :
    OnoBusinessException(
        errorCode = "USER_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "User not found: $userId"
    )

class InvalidCredentialsException :
    OnoBusinessException(
        errorCode = "INVALID_CREDENTIALS",
        httpStatus = HttpStatus.UNAUTHORIZED,
        message = "Invalid email or password"
    )

class PasswordUpdateFailedException(email: String) :
    OnoBusinessException(
        errorCode = "PASSWORD_UPDATE_FAILED",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Failed to update password for user: $email"
    )