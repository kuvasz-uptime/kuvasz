package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.PushMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.services.monitor.MonitorActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageMonitorDataProvider
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import tools.jackson.databind.node.ObjectNode
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class PushMonitorActions(
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val dslContext: DSLContext,
    validator: Validator,
    integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    statCalculator: StatCalculator,
    maintenanceWindowService: MaintenanceWindowService,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
    private val databaseEventHandler: DatabaseEventHandler,
    monitorTypeSupport: PushMonitorTypeSupport,
    statusPageCacheInvalidator: StatusPageCacheInvalidator,
) : StatusPageMonitorDataProvider,
    MonitorActions<PushMonitorRecord, PushMonitorDetailsDto>(
        dslContext,
        appConfig,
        statusPageRepository,
        eventDispatcher,
        statCalculator,
        maintenanceWindowService,
        monitorTypeSupport,
        validator,
        integrationIdValidator,
        statusPageCacheInvalidator,
    ) {

    private val monitorRepository = monitorTypeSupport.repository
    private val pendingFailureRepository = monitorTypeSupport.pendingFailureRepository

    fun getMonitorDetails(monitorId: Long): PushMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows = maintenanceWindowService.getWindowsForMonitor(monitorFromRepo.monitorId())
        return monitorFromRepo.copy(
            effectiveIntegrations = integrationRepository
                .getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet(),
            maintenanceWindows = windows,
            inMaintenance = windows.any { it.active },
        )
    }

    /**
     * Updates the last heartbeat on the push monitor that matches the given client secret and dispatches an UP event
     * conditionally in case the monitor is enabled
     */
    fun updateLastHeartbeat(clientSecret: String, timestamp: OffsetDateTime): PushMonitorRecord? = dslContext
        .transactionResultWithError { config ->
            val txCtx = config.dsl()
            monitorRepository.updateLastHeartbeat(clientSecret, timestamp, txCtx)
                ?.takeIf { it.enabled }
                ?.also { updatedMonitor ->
                    PushMonitorUpEvent(
                        monitor = updatedMonitor,
                        previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(updatedMonitor.id, txCtx),
                    ).also { event ->
                        pendingFailureRepository.deleteByMonitorId(event.monitor.id, txCtx)
                        databaseEventHandler.handleUptimeMonitorEvent(event)
                        eventDispatcher.dispatch(event)
                    }
                }
        }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<PushMonitorDetailsDto> {
        val monitors = monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitors.map { it.monitorId() })
        return monitors.map { detailsDto ->
            val windows = windowsByMonitor[detailsDto.monitorId()].orEmpty()
            detailsDto.copy(
                effectiveIntegrations = integrationRepository
                    .getEffectiveIntegrations(detailsDto.integrations)
                    .toSet(),
                maintenanceWindows = windows,
                inMaintenance = windows.any { it.active },
            )
        }
    }

    fun createMonitor(monitorCreateDto: PushMonitorCreateDto): PushMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { announceCreation() }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): PushMonitorRecord =
        updateMonitor(monitorId, updates, PushMonitor::class.java, PushMonitorUpdateDto::class.java) {
            PushMonitorRecord(it)
        }

    override fun checkUpdateConstraints(existingMonitor: PushMonitorRecord, updatedMonitor: PushMonitorRecord) {
        // Check if the client secret already exists. Need to do it before the actual update, because the
        // constraint is deferred on the client_secret column in PG, and it would be more cumbersome to juggle
        // with nested transactions, than checking it in advance
        val existingBySecret = monitorRepository.findByClientSecret(updatedMonitor.clientSecret)
        if (existingBySecret != null && existingBySecret.id != existingMonitor.id) {
            throw MonitorDuplicatedException()
        }
    }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<PushUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): PushMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            PushMonitorStatsDto(
                id = monitor.id,
                uptimeHistory = uptimeHistory,
            )
        }

    fun getPushMonitorsExport(): List<PushMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPagePushMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
        ) { monitor, uptime ->
            StatusPagePushMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                inMaintenance = uptime.inMaintenance,
                lastHeartbeat = monitor.lastHeartbeat,
            )
        }

    /**
     * Matches an enabled push monitor by the given client secret and dispatches a DOWN event with the given error.
     * The event will be flagged as a manual one to be able to differentiate later and update the error message of a
     * potentially existing event conditionally.
     */
    fun signalFailure(clientSecret: String, error: String): PushMonitorRecord? = dslContext
        .transactionResultWithError { config ->
            val txCtx = config.dsl()
            monitorRepository.findEnabledByClientSecret(clientSecret, txCtx)?.also { monitor ->
                PushMonitorDownEvent(
                    monitor,
                    error,
                    previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitor.id, txCtx),
                    isManual = true,
                ).also { event ->
                    if (event.isDownNow(pendingFailureRepository, txCtx)) {
                        databaseEventHandler.handleUptimeMonitorEvent(event)
                        eventDispatcher.dispatch(event)
                    }
                }
            }
        }
}
