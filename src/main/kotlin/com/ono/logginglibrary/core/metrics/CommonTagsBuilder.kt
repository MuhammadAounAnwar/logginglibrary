package com.ono.logginglibrary.core.metrics

class CommonTagsBuilder {

    private val tags = mutableMapOf<String, String>()

    fun service(name: String) = apply {
        tags["service"] = name
    }

    fun environment(env: String) = apply {
        tags["environment"] = env
    }

    fun version(version: String) = apply {
        tags["version"] = version
    }

    fun region(region: String) = apply {
        tags["region"] = region
    }

    fun custom(key: String, value: String) = apply {
        tags[key] = value
    }

    fun build(): Map<String, String> = tags.toMap()
}