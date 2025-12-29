package com.ono.logginglibrary.logging

import org.springframework.stereotype.Component
import org.zalando.logbook.HttpRequest
import org.zalando.logbook.Logbook
import org.zalando.logbook.attributes.AttributeExtractor
import org.zalando.logbook.attributes.HttpAttributes
import org.zalando.logbook.core.Conditions.*
import org.zalando.logbook.core.DefaultHttpLogWriter
import org.zalando.logbook.core.DefaultSink
import org.zalando.logbook.core.HeaderFilters.authorization
import org.zalando.logbook.core.HeaderFilters.eachHeader
import org.zalando.logbook.core.QueryFilters.accessToken
import org.zalando.logbook.core.QueryFilters.replaceQuery
import org.zalando.logbook.core.attributes.CompositeAttributeExtractor
import org.zalando.logbook.json.JsonBodyFilters.replaceJsonStringProperty
import org.zalando.logbook.json.JsonHttpLogFormatter

class LogsConfig(
    private val originExtractor: OriginExtractor // Inject the component directly
) {
    fun provideLogBook(): Logbook {
        return Logbook.builder()
            .condition(
                exclude(
                    requestTo("/health"),
                    requestTo("/admin/**"),
                    contentType("application/octet-stream"),
                    // Using standard Kotlin sets instead of Lettuce internal sets
                    header("X-Secret") { it in setOf("1", "true") }
                )
            )
            .attributeExtractor(CompositeAttributeExtractor(listOf(originExtractor)))
            .queryFilter(accessToken())
            .queryFilter(replaceQuery("password", "<secret>"))
            // Unified Header Filtering
            .headerFilter(authorization())
            .headerFilter(
                eachHeader { name, value ->
                    // Check if name matches AND value matches our criteria
                    if (name.equals("X-Secret", ignoreCase = true) &&
                        (value == "1" || value.equals("true", ignoreCase = true))
                    ) {
                        "<secret>"
                    } else {
                        // If it doesn't match, return the original value untouched
                        value
                    }
                }
            )
            .bodyFilter(
                replaceJsonStringProperty(
                    setOf("password", "client_secret", "token", "ssn"),
                    "***MASKED***"
                )
            )
            .sink(
                DefaultSink(
                    JsonHttpLogFormatter(),
                    DefaultHttpLogWriter()
                )
            )
            .build()
    }
}

@Component
class OriginExtractor : AttributeExtractor {
    override fun extract(request: HttpRequest): HttpAttributes {
        // "origin" is a useful custom attribute for tracking request source
        return HttpAttributes.of("origin", request.origin.name)
    }
}