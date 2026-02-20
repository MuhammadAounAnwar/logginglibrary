package com.ono.logginglibrary.core.mdc

import org.slf4j.MDC

object MdcUtils {

    fun put(key: String, value: String?) {
        if (value != null) {
            MDC.put(key, value)
        }
    }

    fun get(key: String): String? =
        MDC.get(key)

    fun remove(key: String) {
        MDC.remove(key)
    }

    fun clear() {
        MDC.clear()
    }

    inline fun <T> withMdc(
        key: String,
        value: String?,
        block: () -> T
    ): T {
        put(key, value)
        return try {
            block()
        } finally {
            remove(key)
        }
    }

    inline fun <T> withMdcMap(
        values: Map<String, String>,
        block: () -> T
    ): T {
        values.forEach { (k, v) -> put(k, v) }

        return try {
            block()
        } finally {
            values.keys.forEach { remove(it) }
        }
    }
}