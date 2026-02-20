package com.ono.logginglibrary.core.exception

import org.springframework.http.HttpStatus

abstract class OnoBusinessException(
    val errorCode: String,
    val httpStatus: HttpStatus,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
