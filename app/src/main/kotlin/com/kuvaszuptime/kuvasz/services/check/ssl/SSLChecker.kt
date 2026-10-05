package com.kuvaszuptime.kuvasz.services.check.ssl

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationResult
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.toUri
import jakarta.inject.Singleton

@Singleton
class SSLChecker(
    private val sslValidator: SSLValidator,
    private val eventDispatcher: EventDispatcher,
    private val sslEventRepository: SSLEventRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val proxyRegistry: ProxyRegistry,
) {

    companion object {
        private val logger = loggerFor<SSLChecker>()
    }

    fun check(monitor: HttpMonitorRecord) {
        val proxy = monitor.proxy?.let { name ->
            // The uptime check already reports the missing proxy, an SSL event would only blame the certificate
            proxyRegistry[name] ?: run {
                logger.warn("Skipping the SSL check of monitor (${monitor.name}), its proxy [$name] is not configured")
                return
            }
        }
        val previousEvent = sslEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        when (val result = sslValidator.validateHttps(monitor.url.toUri(), proxy)) {
            is SSLValidationResult.Invalid ->
                SSLInvalidEvent(
                    monitor = monitor,
                    error = result.error,
                    previousEvent = previousEvent
                )
            is SSLValidationResult.Valid -> {
                val certInfo = result.certInfo
                val expiryThresholdDays = monitor.sslExpiryThreshold.toLong()
                if (certInfo.validTo.isBefore(getCurrentTimestamp().plusDays(expiryThresholdDays))) {
                    SSLWillExpireEvent(
                        monitor = monitor,
                        certInfo = certInfo,
                        previousEvent = previousEvent
                    )
                } else {
                    SSLValidEvent(
                        monitor = monitor,
                        certInfo = certInfo,
                        previousEvent = previousEvent
                    )
                }
            }
        }.also { event ->
            databaseEventHandler.handleSSLMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
    }
}
