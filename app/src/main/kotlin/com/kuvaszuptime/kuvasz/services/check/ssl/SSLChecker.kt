package com.kuvaszuptime.kuvasz.services.check.ssl

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toUri
import jakarta.inject.Singleton

@Singleton
class SSLChecker(
    private val sslValidator: SSLValidator,
    private val eventDispatcher: EventDispatcher,
    private val sslEventRepository: SSLEventRepository
) {

    fun check(monitor: HttpMonitorRecord) {
        val previousEvent = sslEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        sslValidator.validateHttps(monitor.url.toUri()).fold(
            { error ->
                eventDispatcher.dispatch(
                    SSLInvalidEvent(
                        monitor = monitor,
                        error = error,
                        previousEvent = previousEvent
                    )
                )
            },
            { certInfo ->
                val expiryThresholdDays = monitor.sslExpiryThreshold.toLong()
                if (certInfo.validTo.isBefore(getCurrentTimestamp().plusDays(expiryThresholdDays))) {
                    eventDispatcher.dispatch(
                        SSLWillExpireEvent(
                            monitor = monitor,
                            certInfo = certInfo,
                            previousEvent = previousEvent
                        )
                    )
                } else {
                    eventDispatcher.dispatch(
                        SSLValidEvent(
                            monitor = monitor,
                            certInfo = certInfo,
                            previousEvent = previousEvent
                        )
                    )
                }
            }
        )
    }
}
