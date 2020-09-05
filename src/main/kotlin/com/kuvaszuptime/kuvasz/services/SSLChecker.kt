package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SSLChecker @Inject constructor(
    private val sslValidator: SSLValidator,
    private val uptimeEventRepository: UptimeEventRepository,
    private val eventDispatcher: EventDispatcher,
    private val sslEventRepository: SSLEventRepository
) {

    companion object {
        private const val EXPIRY_THRESHOLD_DAYS = 30L
    }

    @ExecuteOn(TaskExecutors.IO)
    fun check(monitor: MonitorPojo) {
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
