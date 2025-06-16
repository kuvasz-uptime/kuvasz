package com.kuvaszuptime.kuvasz.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kuvaszuptime.kuvasz.jooq.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.Monitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.models.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import java.time.Duration

@Singleton
class MonitorCrudService(
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val checkScheduler: CheckScheduler,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
) {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModules(JavaTimeModule())

    private val readOnlyMonitorFieldNames = setOf(MONITOR.ID.name, MONITOR.CREATED_AT.name, MONITOR.UPDATED_AT.name)

    fun getMonitorDetails(monitorId: Long): MonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, monitorId),
            nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, monitorId),
            effectiveIntegrations = integrationRepository.getEffectiveIntegrations(monitorFromRepo).toSet()
        )
    }

    fun getMonitorsWithDetails(enabledOnly: Boolean, sortedBy: SortField<*>? = null): List<MonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabledOnly, sortedBy).map { detailsDto ->
            detailsDto.copy(
                nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, detailsDto.id),
                nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, detailsDto.id),
                effectiveIntegrations = integrationRepository.getEffectiveIntegrations(detailsDto).toSet()
            )
        }

    fun createMonitor(monitorCreateDto: MonitorCreateDto): MonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations)).fold(
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
    }

    fun deleteMonitorById(monitorId: Long): Unit =
        monitorRepository.findById(monitorId)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                monitorRepository.deleteById(monitor.id)
                checkScheduler.removeChecksOfMonitor(monitor)
            }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): MonitorRecord =
        try {
            dslContext.transactionResult { config ->
                monitorRepository.findById(monitorId, config.dsl())?.let { existingMonitor ->
                    val toUpdate = existingMonitor.into(Monitor::class.java)
                    val filteredUpdates = updates.fieldNames().asSequence()
                        .filterNot { it in readOnlyMonitorFieldNames }
                        .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                            acc.set(fieldName, updates.get(fieldName))
                        }
                    val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)

                    objectMapper.convertValue<MonitorUpdateDto>(updatedMonitor).let { toValidate ->
                        val errors = validator.validate(toValidate)
                        if (errors.isNotEmpty()) {
                            throw ValidationException(
                                "Validation failed: ${errors.joinToString { "${it.propertyPath}: ${it.message}" }}"
                            )
                        }
                    }
                    // Validate the raw integrations from the DTO
                    updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

                    MonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, config.dsl())
                }
            }.orThrowNotFound(monitorId)
        } catch (ex: DataAccessException) {
            // Cause is encapsulated in the DataAccessException inside a transaction, so we need to unwrap it again here
            // because we're interested in the DuplicationErrors on the call site
            throw ex.cause ?: ex
        }

    private fun MonitorRecord.saveAndReschedule(
        existingMonitor: MonitorRecord,
        txCtx: DSLContext,
    ): MonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).fold(
            { persistenceError -> throw persistenceError },
            { updatedMonitor ->
                if (updatedMonitor.enabled) {
                    checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
                } else {
                    checkScheduler.removeChecksOfMonitor(existingMonitor)
                }
                // If the latency history is disabled, we need to delete all the existing logs
                if (!updatedMonitor.latencyHistoryEnabled && existingMonitor.latencyHistoryEnabled) {
                    latencyLogRepository.deleteAllByMonitorId(existingMonitor.id)
                }
                updatedMonitor
            }
        )

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<UptimeEventDto> =
        monitorRepository.findById(monitorId)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getSSLEventsByMonitorId(monitorId: Long, limit: Int? = null): List<SSLEventDto> =
        monitorRepository.findById(monitorId)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                sslEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): MonitorStatsDto =
        monitorRepository.findById(monitorId)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val statsDto = MonitorStatsDto(
                    id = monitor.id,
                    latencyHistoryEnabled = monitor.latencyHistoryEnabled,
                    averageLatencyInMs = null,
                    minLatencyInMs = null,
                    maxLatencyInMs = null,
                    p90LatencyInMs = null,
                    p95LatencyInMs = null,
                    p99LatencyInMs = null,
                    latencyLogs = emptyList()
                )
                if (!monitor.latencyHistoryEnabled) {
                    return statsDto
                }

                val metrics = latencyLogRepository.getLatencyMetrics(monitor.id, period)
                statsDto.copy(
                    averageLatencyInMs = metrics?.avg,
                    minLatencyInMs = metrics?.min,
                    maxLatencyInMs = metrics?.max,
                    p90LatencyInMs = metrics?.p90,
                    p95LatencyInMs = metrics?.p95,
                    p99LatencyInMs = metrics?.p99,
                    latencyLogs = latencyLogRepository.fetchLatestByMonitorId(monitor.id, period)
                )
            }

    private fun MonitorRecord?.orThrowNotFound(monitorId: Long): MonitorRecord =
        this ?: throw MonitorNotFoundException(monitorId)

    fun getMonitorsExport(): List<MonitorRecord> = monitorRepository.fetchAll()
}
