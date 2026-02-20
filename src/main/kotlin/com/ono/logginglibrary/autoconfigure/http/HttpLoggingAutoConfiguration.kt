package com.ono.logginglibrary.autoconfigure.http

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.zalando.logbook.HttpRequest
import org.zalando.logbook.Logbook
import org.zalando.logbook.attributes.AttributeExtractor
import org.zalando.logbook.attributes.HttpAttributes
import org.zalando.logbook.core.Conditions
import org.zalando.logbook.core.DefaultHttpLogWriter
import org.zalando.logbook.core.DefaultSink
import org.zalando.logbook.core.HeaderFilters
import org.zalando.logbook.core.QueryFilters
import org.zalando.logbook.core.attributes.CompositeAttributeExtractor
import org.zalando.logbook.json.JsonBodyFilters
import org.zalando.logbook.json.JsonHttpLogFormatter
import org.zalando.logbook.spring.webflux.LogbookWebFilter
import java.util.function.Predicate

@AutoConfiguration
@ConditionalOnClass(
    value = [Logbook::class, LogbookWebFilter::class]
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
    prefix = "ono.observability.http",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(HttpLoggingPropertiesBinding::class)
class HttpLoggingAutoConfiguration(
    private val properties: HttpLoggingPropertiesBinding
) {

    @Bean
    @ConditionalOnMissingBean
    fun originExtractor(): AttributeExtractor =
        object : AttributeExtractor {
            override fun extract(request: HttpRequest): HttpAttributes =
                HttpAttributes.of("origin", request.origin.name)
        }

    @Bean
    @ConditionalOnMissingBean
    fun logbook(originExtractor: AttributeExtractor): Logbook {

        val excludeConditions = mutableListOf<Predicate<HttpRequest>>()

        properties.excludePaths.forEach { path ->
            excludeConditions.add(Conditions.requestTo(path))
        }

        excludeConditions.add(Conditions.contentType("application/octet-stream"))

        return Logbook.builder()
            .condition(Conditions.exclude(*excludeConditions.toTypedArray()))
            .attributeExtractor(CompositeAttributeExtractor(listOf(originExtractor)))
            .queryFilter(QueryFilters.accessToken())
            .headerFilter(HeaderFilters.authorization())
            .headerFilter(
                HeaderFilters.eachHeader { name, value ->
                    if (properties.maskHeaders.any { it.equals(name, ignoreCase = true) }) {
                        "<masked>"
                    } else {
                        value
                    }
                }
            )
            .bodyFilter(
                JsonBodyFilters.replaceJsonStringProperty(
                    properties.maskBodyFields.toSet(),
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