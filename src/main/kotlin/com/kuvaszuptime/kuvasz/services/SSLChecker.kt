package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import java.net.URL

@Singleton
class SSLChecker(
    private val sslValidator: SSLValidator,
    private val uptimeEventRepository: UptimeEventRepository,
    private val eventDispatcher: EventDispatcher,
    private val sslEventRepository: SSLEventRepository
) {

    companion object {
        private const val EXPIRY_THRESHOLD_DAYS = 30L
    }

    fun check(monitor: MonitorRecord) {
        if (uptimeEventRepository.isMonitorUp(monitor.id)) {
            val previousEvent = sslEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
            sslValidator.validate(URL(monitor.url)).fold(
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
                    if (certInfo.validTo.isBefore(getCurrentTimestamp().plusDays(EXPIRY_THRESHOLD_DAYS))) {
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
}
