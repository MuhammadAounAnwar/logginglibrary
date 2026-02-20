package com.ono.logginglibrary.autoconfigure.http

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ono.observability.http")
data class HttpLoggingPropertiesBinding(
    val enabled: Boolean = true,
    val excludePaths: List<String> = listOf(
        "/actuator/**",
        "/health",
        "/health/**"
    ),
    val maskHeaders: List<String> = listOf(
        "Authorization",
        "X-API-Key",
        "Cookie",
        "Set-Cookie"
    ),
    val maskBodyFields: List<String> = listOf(
        "password",
        "token",
        "secret",
        "apiKey",
        "accessToken",
        "refreshToken"
    )
)