package com.kuvaszuptime.kuvasz.services.check.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.HttpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import java.time.Duration

@Singleton
class HttpMonitorCrudService(
    private val monitorRepository: HttpMonitorRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val checkScheduler: HttpCheckScheduler,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
) {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModules(JavaTimeModule())

    fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, monitorId),
            nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, monitorId),
            effectiveIntegrations = integrationRepository.getEffectiveIntegrations(monitorFromRepo).toSet()
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sslStatus: List<SslStatus> = emptyList(),
        sslCheckEnabled: Boolean? = null,
        sortedBy: SortField<*>? = null,
    ): List<HttpMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sslStatus, sslCheckEnabled, sortedBy)
            .map { detailsDto ->
                detailsDto.copy(
                    nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, detailsDto.id),
                    nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, detailsDto.id),
                    effectiveIntegrations = integrationRepository.getEffectiveIntegrations(detailsDto).toSet()
                )
            }

    fun createMonitor(monitorCreateDto: HttpMonitorCreateDto): HttpMonitorRecord {
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
                eventDispatcher.dispatch(HttpMonitorDeleteEvent(monitor.id))
            }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorRecord {
        val result = try {
            dslContext.transactionResult { config ->
                monitorRepository.findById(monitorId, config.dsl())?.let { existingMonitor ->
                    val toUpdate = existingMonitor.into(HttpMonitor::class.java)
                    val filteredUpdates = updates.fieldNames().asSequence()
                        .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                            acc.set(fieldName, updates.get(fieldName))
                        }
                    val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)

                    objectMapper.convertValue<HttpMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                        val errors = validator.validate(toValidate)
                        if (errors.isNotEmpty()) {
                            throw ValidationException(
                                "Validation failed: ${errors.joinToString { "${it.propertyPath}: ${it.message}" }}"
                            )
                        }
                    }
                    // Validate the raw integrations from the DTO
                    updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

                    HttpMonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, config.dsl())
                }
            }.orThrowNotFound(monitorId)
        } catch (ex: DataAccessException) {
            // Cause is encapsulated in the DataAccessException inside a transaction, so we need to unwrap it again here
            // because we're interested in the DuplicationErrors on the call site
            throw ex.cause ?: ex
        }

        return result.also { eventDispatcher.dispatch(HttpMonitorUpdateEvent(it.id)) }
    }

    private fun HttpMonitorRecord.saveAndReschedule(
        existingMonitor: HttpMonitorRecord,
        txCtx: DSLContext,
    ): HttpMonitorRecord =
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

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<HttpUptimeEventDto> =
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

    fun getMonitorStats(monitorId: Long, period: Duration): HttpMonitorStatsDto =
        monitorRepository.findById(monitorId)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val statsDto = HttpMonitorStatsDto(
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

    private fun HttpMonitorRecord?.orThrowNotFound(monitorId: Long): HttpMonitorRecord =
        this ?: throw MonitorNotFoundException(monitorId)

    fun getHttpMonitorsExport(): List<HttpMonitorRecord> = monitorRepository.fetchAll()
}
