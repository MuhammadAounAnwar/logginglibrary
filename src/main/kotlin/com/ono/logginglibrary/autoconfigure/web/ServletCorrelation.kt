package com.ono.logginglibrary.autoconfigure.web

import com.ono.logginglibrary.core.correlation.CorrelationIds
import jakarta.servlet.http.HttpServletRequest

/**
 * The correlation id the filter settled on for this request: the request attribute first, then the MDC of this thread, and only
 * then the raw request header. Error handlers use this for the `correlationId` field of an error body, so the body, the response
 * header and the log lines all carry the same id even when the caller sent none.
 */
val HttpServletRequest.correlationId: String?
    get() = getAttribute(CorrelationIds.ATTRIBUTE) as? String
        ?: CorrelationIds.current()
        ?: CorrelationIds.sanitize(getHeader(CorrelationIds.HEADER))
        ?: CorrelationIds.sanitize(getHeader(CorrelationIds.ALT_HEADER))
