package com.ono.logginglibrary.core

object ObservabilityConstants {

    const val DEFAULT_CORRELATION_HEADER = "X-Correlation-Id"
    const val DEFAULT_TRACE_ID_KEY = "traceId"
    const val DEFAULT_SPAN_ID_KEY = "spanId"

    const val DEFAULT_CORRELATION_ID_KEY = "correlationId"
    const val DEFAULT_SERVICE_KEY = "service"
    const val DEFAULT_ENV_KEY = "environment"
    const val DEFAULT_VERSION_KEY = "version"

    const val DEFAULT_HTTP_REQUEST_TIMER = "http.server.requests"
    const val DEFAULT_METHOD_EXECUTION_TIMER = "method.execution"
}