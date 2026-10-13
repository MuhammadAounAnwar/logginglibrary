package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.core.correlation.CorrelationIds
import org.springframework.web.server.ServerWebExchange

/**
 * The correlation id the web filter settled on for this exchange: the exchange attribute first (it does not depend on MDC
 * propagation), then the MDC, and only then the raw request header. See the servlet twin in `ServletCorrelation.kt`.
 */
val ServerWebExchange.correlationId: String?
    get() = getAttribute<String>(CorrelationIds.ATTRIBUTE)
        ?: CorrelationIds.current()
        ?: CorrelationIds.sanitize(request.headers.getFirst(CorrelationIds.HEADER))
        ?: CorrelationIds.sanitize(request.headers.getFirst(CorrelationIds.ALT_HEADER))
