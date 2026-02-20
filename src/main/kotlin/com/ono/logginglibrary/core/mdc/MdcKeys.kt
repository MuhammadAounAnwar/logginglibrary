package com.ono.logginglibrary.core.mdc

import com.ono.logginglibrary.core.ObservabilityConstants

object MdcKeys {
    const val CORRELATION_ID = ObservabilityConstants.DEFAULT_CORRELATION_ID_KEY
    const val TRACE_ID = ObservabilityConstants.DEFAULT_TRACE_ID_KEY
    const val SPAN_ID = ObservabilityConstants.DEFAULT_SPAN_ID_KEY
    const val SERVICE = ObservabilityConstants.DEFAULT_SERVICE_KEY
    const val ENVIRONMENT = ObservabilityConstants.DEFAULT_ENV_KEY
    const val VERSION = ObservabilityConstants.DEFAULT_VERSION_KEY
}