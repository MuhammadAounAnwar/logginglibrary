package com.ono.logginglibrary.core.mdc

import java.util.*

fun interface CorrelationIdGenerator {
    fun generate(): String

    companion object {
        val DEFAULT: CorrelationIdGenerator =
            CorrelationIdGenerator { UUID.randomUUID().toString() }
    }
}