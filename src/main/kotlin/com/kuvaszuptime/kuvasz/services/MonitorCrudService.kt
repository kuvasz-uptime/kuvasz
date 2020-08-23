package com.kuvaszuptime.kuvasz.services

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.toOption
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorCrudService @Inject constructor(
    private val monitorRepository: MonitorRepository,
    private val checkScheduler: CheckScheduler
) {

    fun getMonitorDetails(monitorId: Int): Option<MonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(false, monitorId).firstOrNone()

    fun getMonitorsWithDetails(enabledOnly: Boolean): List<MonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabledOnly)

    fun getMonitor(monitorId: Int): Option<MonitorPojo> = monitorRepository.findById(monitorId).toOption()

    fun getMonitors(enabledOnly: Boolean): List<MonitorPojo> =
        monitorRepository.getMonitors(enabledOnly)

    fun createMonitor(monitorCreateDto: MonitorCreateDto): MonitorPojo =
        monitorRepository.returningInsert(monitorCreateDto.toMonitorPojo()).fold(
            { persistenceError -> throw persistenceError },
            { insertedMonitor ->
                if (insertedMonitor.enabled) {
                    checkScheduler.createChecksForMonitor(insertedMonitor).mapLeft { schedulingError ->
                        monitorRepository.deleteById(insertedMonitor.id)
                        throw schedulingError
                    }
                }
                insertedMonitor
            }
        )

    fun deleteMonitorById(monitorId: Int) = monitorRepository.findById(monitorId).toOption().fold(
        { throw MonitorNotFoundError(monitorId) },
        { monitorPojo ->
            monitorRepository.deleteById(monitorPojo.id)
            checkScheduler.removeChecksOfMonitor(monitorPojo)
        }
    )

    fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorPojo =
        monitorRepository.findById(monitorId).toOption().fold(
            { throw MonitorNotFoundError(monitorId) },
            { existingMonitor ->
                val updatedMonitor = MonitorPojo().apply {
                    id = existingMonitor.id
                    name = monitorUpdateDto.name ?: existingMonitor.name
                    url = monitorUpdateDto.url ?: existingMonitor.url
                    uptimeCheckInterval = monitorUpdateDto.uptimeCheckInterval ?: existingMonitor.uptimeCheckInterval
                    enabled = monitorUpdateDto.enabled ?: existingMonitor.enabled
                }

                updatedMonitor.saveAndReschedule(existingMonitor)
            }
        )

    private fun MonitorPojo.saveAndReschedule(existingMonitor: MonitorPojo): MonitorPojo =
        monitorRepository.returningUpdate(this).fold(
            { persistenceError -> throw persistenceError },
            { updatedMonitor ->
                if (updatedMonitor.enabled) {
                    checkScheduler.updateChecksForMonitor(existingMonitor, updatedMonitor).fold(
                        { schedulingError -> throw schedulingError },
                        { updatedMonitor }
                    )
                } else {
                    checkScheduler.removeChecksOfMonitor(existingMonitor)
                    updatedMonitor
                }
            }
        )
}
