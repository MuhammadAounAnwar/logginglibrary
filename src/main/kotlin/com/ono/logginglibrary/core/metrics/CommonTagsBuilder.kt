package com.ono.logginglibrary.core.metrics

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

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

    fun build(): Tags = Tags.of(tags.entries.map { Tag.of(it.key, it.value) })
}
