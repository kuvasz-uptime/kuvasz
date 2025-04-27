package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.dto.*
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

@Singleton
class MonitorCrudService(
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val checkScheduler: CheckScheduler,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
) {

    fun getMonitorDetails(monitorId: Int): MonitorDetailsDto? =
        monitorRepository.getMonitorWithDetails(monitorId)?.let { detailsDto ->
            if (detailsDto.latencyHistoryEnabled) {
                val latencies = latencyLogRepository.getLatencyPercentiles(detailsDto.id).firstOrNull()
                detailsDto.copy(
                    p95LatencyInMs = latencies?.p95,
                    p99LatencyInMs = latencies?.p99
                )
            } else detailsDto
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

    fun createMonitor(monitorCreateDto: MonitorCreateDto): MonitorRecord =
        monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord()).fold(
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
        monitorRepository.findById(monitorId)?.let { monitor ->
            monitorRepository.deleteById(monitor.id)
            checkScheduler.removeChecksOfMonitor(monitor)
        } ?: throw MonitorNotFoundError(monitorId)

    fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorRecord =
        try {
            dslContext.transactionResult { config ->
                monitorRepository.findById(monitorId, config.dsl())?.let { existingMonitor ->
                    val updatedMonitor = prepareUpdatePojo(monitorUpdateDto, existingMonitor)

                    updatedMonitor.saveAndReschedule(existingMonitor, config.dsl())
                }
            } ?: throw MonitorNotFoundError(monitorId)
        } catch (ex: DataAccessException) {
            // Cause is encapsulated in the DataAccessException inside a transaction, so we need to unwrap it again here
            // because we're interested in the DuplicationErrors on the call site
            throw ex.cause ?: ex
        }

    private fun prepareUpdatePojo(monitorUpdateDto: MonitorUpdateDto, existingMonitor: MonitorRecord) =
        MonitorRecord().apply {
            id = existingMonitor.id
            name = monitorUpdateDto.name ?: existingMonitor.name
            url = monitorUpdateDto.url ?: existingMonitor.url
            uptimeCheckInterval =
                monitorUpdateDto.uptimeCheckInterval ?: existingMonitor.uptimeCheckInterval
            enabled = monitorUpdateDto.enabled ?: existingMonitor.enabled
            sslCheckEnabled = monitorUpdateDto.sslCheckEnabled ?: existingMonitor.sslCheckEnabled
            pagerdutyIntegrationKey = existingMonitor.pagerdutyIntegrationKey
            requestMethod = monitorUpdateDto.requestMethod ?: existingMonitor.requestMethod
            latencyHistoryEnabled =
                monitorUpdateDto.latencyHistoryEnabled ?: existingMonitor.latencyHistoryEnabled
            forceNoCache = monitorUpdateDto.forceNoCache ?: existingMonitor.forceNoCache
            followRedirects = monitorUpdateDto.followRedirects ?: existingMonitor.followRedirects
        }

    private fun MonitorRecord.saveAndReschedule(
        existingMonitor: MonitorRecord,
        txCtx: DSLContext,
    ): MonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).fold(
            { persistenceError -> throw persistenceError },
            { updatedMonitor ->
                if (updatedMonitor.enabled) {
                    checkScheduler.updateChecksForMonitor(existingMonitor, updatedMonitor)?.let { throw it }
                } else {
                    checkScheduler.removeChecksOfMonitor(existingMonitor)
                }
                if (!this.latencyHistoryEnabled && existingMonitor.latencyHistoryEnabled) {
                    latencyLogRepository.deleteAllByMonitorId(existingMonitor.id)
                }
                updatedMonitor
            }
        )

    fun updatePagerdutyIntegrationKey(monitorId: Int, integrationKey: String?): MonitorRecord =
        dslContext.transactionResult { config ->
            monitorRepository.findById(monitorId, config.dsl())?.let { existingMonitor ->
                val updatedMonitor = existingMonitor.setPagerdutyIntegrationKey(integrationKey)
                updatedMonitor.saveAndReschedule(existingMonitor, config.dsl())
            }
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
