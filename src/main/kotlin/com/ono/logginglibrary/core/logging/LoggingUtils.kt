package com.ono.logginglibrary.core.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

object LoggingUtils {

    fun logger(forClass: KClass<*>): Logger =
        LoggerFactory.getLogger(forClass.java)

    inline fun <reified T : Any> logger(): Logger =
        LoggerFactory.getLogger(T::class.java)
}