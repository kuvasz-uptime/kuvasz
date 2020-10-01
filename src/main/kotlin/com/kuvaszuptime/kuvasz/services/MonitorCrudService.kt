package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import javax.inject.Singleton

@Singleton
class MonitorCrudService(
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val checkScheduler: CheckScheduler,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository
) {

    fun getMonitorDetails(monitorId: Int): MonitorDetailsDto? =
        monitorRepository.getMonitorWithDetails(monitorId)?.let { detailsDto ->
            val latencies = latencyLogRepository.getLatencyPercentiles(detailsDto.id).firstOrNull()
            detailsDto.copy(
                p95LatencyInMs = latencies?.p95,
                p99LatencyInMs = latencies?.p99
            )
        }

    fun getMonitorsWithDetails(enabledOnly: Boolean): List<MonitorDetailsDto> {
        val latencies = latencyLogRepository.getLatencyPercentiles()

        return monitorRepository.getMonitorsWithDetails(enabledOnly).map { detailsDto ->
            val matchingLatency = latencies.find { it.monitorId == detailsDto.id }
            detailsDto.copy(
                p95LatencyInMs = matchingLatency?.p95,
                p99LatencyInMs = matchingLatency?.p99
            )
        }
    }

    fun createMonitor(monitorCreateDto: MonitorCreateDto): MonitorPojo =
        monitorRepository.returningInsert(monitorCreateDto.toMonitorPojo()).fold(
            { persistenceError -> throw persistenceError },
            { insertedMonitor ->
                if (insertedMonitor.enabled) {
                    checkScheduler.createChecksForMonitor(insertedMonitor)?.let { schedulingError ->
                        monitorRepository.deleteById(insertedMonitor.id)
                        throw schedulingError
                    }
                }
                insertedMonitor
            }
        )

    fun deleteMonitorById(monitorId: Int): Unit =
        monitorRepository.findById(monitorId)?.let { monitorPojo ->
            monitorRepository.deleteById(monitorPojo.id)
            checkScheduler.removeChecksOfMonitor(monitorPojo)
        } ?: throw MonitorNotFoundError(monitorId)

    fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorPojo =
        monitorRepository.findById(monitorId)?.let { existingMonitor ->
            val updatedMonitor = MonitorPojo().apply {
                id = existingMonitor.id
                name = monitorUpdateDto.name ?: existingMonitor.name
                url = monitorUpdateDto.url ?: existingMonitor.url
                uptimeCheckInterval = monitorUpdateDto.uptimeCheckInterval ?: existingMonitor.uptimeCheckInterval
                enabled = monitorUpdateDto.enabled ?: existingMonitor.enabled
                sslCheckEnabled = monitorUpdateDto.sslCheckEnabled ?: existingMonitor.sslCheckEnabled
                pagerdutyIntegrationKey = existingMonitor.pagerdutyIntegrationKey
            }

            updatedMonitor.saveAndReschedule(existingMonitor)
        } ?: throw MonitorNotFoundError(monitorId)

    private fun MonitorPojo.saveAndReschedule(existingMonitor: MonitorPojo): MonitorPojo =
        monitorRepository.returningUpdate(this).fold(
            { persistenceError -> throw persistenceError },
            { updatedMonitor ->
                if (updatedMonitor.enabled) {
                    checkScheduler.updateChecksForMonitor(existingMonitor, updatedMonitor)?.let { throw it }
                } else {
                    checkScheduler.removeChecksOfMonitor(existingMonitor)
                }
                updatedMonitor
            }
        )

    fun updatePagerdutyIntegrationKey(monitorId: Int, integrationKey: String?): MonitorPojo =
        monitorRepository.findById(monitorId)?.let { existingMonitor ->
            val updatedMonitor = existingMonitor.setPagerdutyIntegrationKey(integrationKey)
            updatedMonitor.saveAndReschedule(existingMonitor)
        } ?: throw MonitorNotFoundError(monitorId)

    fun getUptimeEventsByMonitorId(monitorId: Int): List<UptimeEventDto> =
        monitorRepository.findById(monitorId)?.let { _ ->
            uptimeEventRepository.getEventsByMonitorId(monitorId)
        } ?: throw MonitorNotFoundError(monitorId)

    fun getSSLEventsByMonitorId(monitorId: Int): List<SSLEventDto> =
        monitorRepository.findById(monitorId)?.let { _ ->
            sslEventRepository.getEventsByMonitorId(monitorId)
        } ?: throw MonitorNotFoundError(monitorId)
}
