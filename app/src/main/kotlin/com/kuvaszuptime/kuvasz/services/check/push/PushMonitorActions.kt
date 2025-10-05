package com.kuvaszuptime.kuvasz.services.check.push

import arrow.core.getOrHandle
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.PushMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.push.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.monitor.MonitorActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageMonitorDataProvider
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import java.time.Duration

@Singleton
class PushMonitorActions(
    private val monitorRepository: PushMonitorRepository,
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
//    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
) : StatusPageMonitorDataProvider,
    MonitorActions<PushMonitorRecord>(dslContext, appConfig, statusPageRepository, monitorRepository, eventDispatcher) {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModules(JavaTimeModule())

    fun getMonitorDetails(monitorId: Long): PushMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        return monitorFromRepo.copy(
//            effectiveIntegrations = integrationRepository.getEffectiveIntegrations(monitorFromRepo).toSet() // TODO
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<PushMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
            .map { detailsDto ->
                detailsDto.copy(
                    // TODO
//                    nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, detailsDto.id),
//                    nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, detailsDto.id),
//                    effectiveIntegrations = integrationRepository.getEffectiveIntegrations(detailsDto).toSet()
                )
            }

//    fun createMonitor(monitorCreateDto: HttpMonitorCreateDto): PushMonitorRecord {
//        // Validate the raw integrations from the DTO
//        val validatedIntegrations =
//            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())
//
//        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations)).fold(
//            { persistenceError -> throw persistenceError },
//            { insertedMonitor ->
//                if (insertedMonitor.enabled) {
//                    checkScheduler.createChecksForMonitor(insertedMonitor)?.let { schedulingError ->
//                        monitorRepository.deleteById(insertedMonitor.id)
//                        throw schedulingError
//                    }
//                }
//                insertedMonitor
//            }
//        )
//    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): PushMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingMonitor.into(PushMonitor::class.java)
            val filteredUpdates = updates.fieldNames().asSequence()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
                throw ReadOnlyMonitorNameException()
            }

            objectMapper.convertValue<PushMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            monitorRepository
                .returningUpdate(PushMonitorRecord(updatedMonitor), txCtx)
                .getOrHandle { throw it } // Triggering the exception to rollback the transaction
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<PushUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getSSLEventsByMonitorId(monitorId: Long, limit: Int? = null): List<SSLEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                sslEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

//    fun getMonitorStats(monitorId: Long, period: Duration): HttpMonitorStatsDto =
//        monitorRepository.findById(monitorId)
//            .orThrowNotFound(monitorId)
//            .let { monitor ->
//                val uptimeHistory = statCalculator.calculateHistoricalHttpUptimeStats(period, monitorId)
//                val statsDto = HttpMonitorStatsDto(
//                    id = monitor.id,
//                    uptimeHistory = uptimeHistory,
//                    latencyHistoryEnabled = monitor.latencyHistoryEnabled,
//                    latencyStats = null,
//                    latencyLogs = emptyList()
//                )
//                if (!monitor.latencyHistoryEnabled) {
//                    return statsDto
//                }
//
//                val metrics = latencyLogRepository.getLatencyMetrics(monitor.id, period)
//                statsDto.copy(
//                    latencyStats = metrics?.let {
//                        LatencyStatsDto(
//                            averageLatencyInMs = metrics.avg,
//                            minLatencyInMs = metrics.min,
//                            maxLatencyInMs = metrics.max,
//                            p90LatencyInMs = metrics.p90,
//                            p95LatencyInMs = metrics.p95,
//                            p99LatencyInMs = metrics.p99,
//                        )
//                    },
//                    latencyLogs = latencyLogRepository.fetchLatestByMonitorId(monitor.id, period)
//                )
//            }

    fun getPushMonitorsExport(): List<PushMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageMonitorDetailsDto> {
        val pushMonitorNames = monitorIds?.filter { it.type == MonitorType.PUSH }?.map { it.name }
        val enabledMonitors = monitorRepository.getMonitorsWithDetails(enabled = true, monitorNames = pushMonitorNames)

        return enabledMonitors.map { monitor ->
            val uptimeHistory = statCalculator.calculateHistoricalPushUptimeStats(period, monitor.id)
            val statusHistory = statCalculator.generateUptimeHistoryOverview(
                period = period,
                uptimeEvents = uptimeEventRepository.fetchAllInPeriod(
                    period = period,
                    monitorId = monitor.id,
                )
            )
            StatusPageMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = null,
                uptimeRatio = uptimeHistory.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = statusHistory,
            )
        }
    }
}
