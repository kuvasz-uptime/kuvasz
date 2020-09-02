package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SSLChecker @Inject constructor(
    private val sslValidator: SSLValidator,
    private val monitorCrudService: MonitorCrudService,
    private val eventDispatcher: EventDispatcher,
    private val sslEventRepository: SSLEventRepository
) {

    @ExecuteOn(TaskExecutors.IO)
    fun check(monitor: MonitorPojo) {
        if (monitorCrudService.isMonitorUp(monitor.id)) {
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
                    // TODO "will expire events
                    eventDispatcher.dispatch(
                        SSLValidEvent(
                            monitor = monitor,
                            certInfo = certInfo,
                            previousEvent = previousEvent
                        )
                    )
                }
            )
        }
    }
}
