package com.ono.logginglibrary.core.exception

import java.time.Instant

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
    val correlationId: String?,
    val timestamp: Instant = Instant.now()
)