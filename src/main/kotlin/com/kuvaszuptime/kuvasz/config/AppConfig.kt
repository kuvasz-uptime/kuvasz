package com.kuvaszuptime.kuvasz.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.Min

@ConfigurationProperties("app-config")
@Context
@Introspected
class AppConfig {
    companion object {
        private const val MIN_RETENTION_DAYS = 7L
        private const val DEFAULT_RETENTION_DAYS = 30
    }

    @Min(MIN_RETENTION_DAYS)
    var dataRetentionDays: Int = DEFAULT_RETENTION_DAYS
}
