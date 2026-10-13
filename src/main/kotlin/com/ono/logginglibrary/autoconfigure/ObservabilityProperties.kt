package com.ono.logginglibrary.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ono.observability")
data class ObservabilityProperties(

    var enabled: Boolean = true,

    var logging: Logging = Logging(),
    var metrics: Metrics = Metrics(),
    var aop: Aop = Aop(),
    var exceptions: Exceptions = Exceptions(),
    var correlation: Correlation = Correlation()

) {

    data class Logging(
        var enabled: Boolean = true,
        var correlationHeader: String = "X-Correlation-Id"
    )

    data class Metrics(
        var enabled: Boolean = true,
        var serviceName: String = "unknown-service"
    )

    data class Aop(
        var enabled: Boolean = true,
        var slowThresholdMs: Long = 500
    )

    data class Correlation(
        /** Set `app.correlation_id` on PostgreSQL connections so the replication outbox trigger can record it. Off by default. */
        var databaseSession: Boolean = false
    )

    data class Exceptions(
        var enabled: Boolean = true
    )
}