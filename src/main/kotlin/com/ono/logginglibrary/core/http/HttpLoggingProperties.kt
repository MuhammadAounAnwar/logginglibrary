package com.ono.logginglibrary.core.http

open class HttpLoggingProperties(

    val enabled: Boolean = true,

    val excludePaths: List<String> = listOf(
        "/health",
        "/admin/**"
    ),

    val maskBodyFields: List<String> = listOf(
        "password",
        "client_secret",
        "token",
        "ssn"
    ),

    val maskHeaders: List<String> = listOf(
        "Authorization",
        "X-Secret"
    )
)