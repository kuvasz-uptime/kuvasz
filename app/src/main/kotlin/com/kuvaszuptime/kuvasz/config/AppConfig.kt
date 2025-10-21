package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
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
        private const val UPTIME_CHECK_LOCK_TIMEOUT_MS = 300_000L // 5 minutes
    }

    @Min(MIN_EVENT_RETENTION_DAYS, message = ValidationMessages.APP_CONFIG_EVENT_RETENTION_DAYS_MIN)
    var eventDataRetentionDays: Int = DEFAULT_EVENT_RETENTION_DAYS

    @Min(MIN_LATENCY_RETENTION_DAYS, message = ValidationMessages.APP_CONFIG_LATENCY_RETENTION_DAYS_MIN)
    var latencyDataRetentionDays: Int = DEFAULT_LATENCY_RETENTION_DAYS

    var language: String = DEFAULT_LANGUAGE

    var logEventHandler: Boolean = false

    var checkUpdates: Boolean = true

    private var isHttpMonitorExternalWriteDisabled = false
    private var isPushMonitorExternalWriteDisabled = false

    private var isStatusPageExternalWriteDisabled = false

    var uptimeCheckLockTimeoutMs: Long = UPTIME_CHECK_LOCK_TIMEOUT_MS

    fun disableHttpMonitorExternalWrite() {
        isHttpMonitorExternalWriteDisabled = true
    }

    fun disablePushMonitorExternalWrite() {
        isPushMonitorExternalWriteDisabled = true
    }

    fun disableStatusPageExternalWrite() {
        isStatusPageExternalWriteDisabled = true
    }

    /**
     * INTENDED TO BE USED IN TESTS ONLY (not nice ofc) to revert the manually toggled disabled state
     */
    fun enableStatusPageExternalWrite() {
        isStatusPageExternalWriteDisabled = false
    }

    fun isHttpMonitorExternalWriteDisabled() = isHttpMonitorExternalWriteDisabled

    fun isPushMonitorExternalWriteDisabled() = isPushMonitorExternalWriteDisabled

    fun isStatusPageExternalWriteDisabled() = isStatusPageExternalWriteDisabled
}
