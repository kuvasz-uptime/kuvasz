package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.TcpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.TcpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageTcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.tcp.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.tcp.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.toStatsDto
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.services.monitor.MonitorActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageMonitorDataProvider
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Duration

@Singleton
class TcpMonitorActions(
    private val monitorRepository: TcpMonitorRepository,
    private val checkScheduler: TcpCheckScheduler,
    private val uptimeEventRepository: TcpUptimeEventRepository,
    private val metricsLogRepository: TcpMetricsLogRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    statCalculator: StatCalculator,
    maintenanceWindowService: MaintenanceWindowService,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
) : StatusPageMonitorDataProvider,
    MonitorActions<TcpMonitorRecord, TcpMonitorDetailsDto>(
        dslContext,
        appConfig,
        statusPageRepository,
        monitorRepository,
        eventDispatcher,
        statCalculator,
        maintenanceWindowService,
    ) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMonitorDetails(monitorId: Long): TcpMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows = maintenanceWindowService.getWindowsForMonitor(monitorFromRepo.monitorId())

        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(monitorId),
            effectiveIntegrations = integrationRepository
                .getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet(),
            maintenanceWindows = windows,
            inMaintenance = windows.any { it.active },
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<TcpMonitorDetailsDto> {
        val monitors = monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitors.map { it.monitorId() })

        return monitors.map { detailsDto ->
            val windows = windowsByMonitor[detailsDto.monitorId()].orEmpty()
            detailsDto.copy(
                nextUptimeCheck = checkScheduler.getNextCheck(detailsDto.id),
                effectiveIntegrations = integrationRepository
                    .getEffectiveIntegrations(detailsDto.integrations)
                    .toSet(),
                maintenanceWindows = windows,
                inMaintenance = windows.any { it.active },
            )
        }
    }

    fun createMonitor(monitorCreateDto: TcpMonitorCreateDto): TcpMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { createdMonitor ->
                checkScheduler.createChecksForMonitor(createdMonitor)
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): TcpMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingMonitor.into(TcpMonitor::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
                throw ReadOnlyMonitorNameException()
            }

            objectMapper.convertValue<TcpMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            TcpMonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, txCtx)
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    private fun TcpMonitorRecord.saveAndReschedule(
        existingMonitor: TcpMonitorRecord,
        txCtx: DSLContext,
    ): TcpMonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).also { updatedMonitor ->
            if (updatedMonitor.enabled) {
                checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
            } else {
                checkScheduler.removeChecksOfMonitor(existingMonitor)
            }
            // If the metrics history is disabled, we need to delete all the existing logs
            if (!updatedMonitor.metricsHistoryEnabled && existingMonitor.metricsHistoryEnabled) {
                metricsLogRepository.deleteAllByMonitorId(existingMonitor.id, txCtx)
            }
        }

    fun deleteMonitorById(monitorId: Long) =
        super.deleteMonitorById(monitorId) { monitor ->
            checkScheduler.removeChecksOfMonitor(monitor)
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<TcpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): TcpMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = TcpMonitorStatsDto(
                id = monitor.id,
                metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                uptimeHistory = uptimeHistory,
                latencyStats = null,
                metricsLogs = emptyList(),
            )
            if (!monitor.metricsHistoryEnabled) {
                return@withUptimeHistory statsDto
            }
            statsDto.copy(
                latencyStats = metricsLogRepository.getLatencyMetrics(monitor.id, period)?.toStatsDto(),
                metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
            )
        }

    fun getTcpMonitorsExport(): List<TcpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageTcpMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
        ) { monitor, uptime ->
            val latencyMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getLatencyMetrics(monitor.id, period) }

            StatusPageTcpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                inMaintenance = uptime.inMaintenance,
            )
        }
}
