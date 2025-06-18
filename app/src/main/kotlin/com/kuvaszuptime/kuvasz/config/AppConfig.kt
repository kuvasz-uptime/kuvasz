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
        private const val MIN_EVENT_RETENTION_DAYS = 1L
        private const val MIN_LATENCY_RETENTION_DAYS = 1L
        private const val DEFAULT_EVENT_RETENTION_DAYS = 365
        private const val DEFAULT_LATENCY_RETENTION_DAYS = 7
        private const val DEFAULT_LANGUAGE = "en"
    }

    @Min(MIN_EVENT_RETENTION_DAYS)
    var eventDataRetentionDays: Int = DEFAULT_EVENT_RETENTION_DAYS

    @Min(MIN_LATENCY_RETENTION_DAYS)
    var latencyDataRetentionDays: Int = DEFAULT_LATENCY_RETENTION_DAYS

    var language: String = DEFAULT_LANGUAGE

    private var isExternalWriteDisabled = false

    fun disableExternalWrite() {
        isExternalWriteDisabled = true
    }

    fun isExternalWriteDisabled() = isExternalWriteDisabled
}
