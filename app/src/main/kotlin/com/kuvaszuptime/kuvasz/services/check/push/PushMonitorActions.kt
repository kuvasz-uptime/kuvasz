package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.PushMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.push.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
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
import java.time.OffsetDateTime

@Singleton
class PushMonitorActions(
    private val monitorRepository: PushMonitorRepository,
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
    private val databaseEventHandler: DatabaseEventHandler,
    private val pendingFailureRepository: PendingFailureRepository,
) : StatusPageMonitorDataProvider,
    MonitorActions<PushMonitorRecord>(dslContext, appConfig, statusPageRepository, monitorRepository, eventDispatcher) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMonitorDetails(monitorId: Long): PushMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        return monitorFromRepo.copy(
            effectiveIntegrations = integrationRepository
                .getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet()
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
                        pendingFailureRepository.deleteByMonitorId(event.monitor.id)
                        databaseEventHandler.handleUptimeMonitorEvent(event)
                        eventDispatcher.dispatch(event)
                    }
                }
        }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<PushMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
            .map { detailsDto ->
                detailsDto.copy(
                    effectiveIntegrations = integrationRepository
                        .getEffectiveIntegrations(detailsDto.integrations)
                        .toSet()
                )
            }

    fun createMonitor(monitorCreateDto: PushMonitorCreateDto): PushMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): PushMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingById = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingById.into(PushMonitor::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingById.name && !isMonitorChangeable(existingById)) {
                throw ReadOnlyMonitorNameException()
            }
            // Check if the client secret already exists. Need to do it before the actual update, because the
            // constraint is deferred on the client_secret column in PG, and it would be more cumbersome to juggle
            // with nested transactions, than checking it in advance
            val existingBySecret = monitorRepository.findByClientSecret(updatedMonitor.clientSecret)
            if (existingBySecret != null && existingBySecret.id != existingById.id) {
                throw MonitorDuplicatedException()
            }

            objectMapper.convertValue<PushMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            monitorRepository.returningUpdate(PushMonitorRecord(updatedMonitor), txCtx)
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<PushUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): PushMonitorStatsDto =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val uptimeHistory = statCalculator.calculateHistoricalPushUptimeStats(period, monitorId)
                PushMonitorStatsDto(
                    id = monitor.id,
                    uptimeHistory = uptimeHistory,
                )
            }

    fun getPushMonitorsExport(): List<PushMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPagePushMonitorDetailsDto> {
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
            StatusPagePushMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                uptimeRatio = uptimeHistory.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = statusHistory,
                lastHeartbeat = monitor.lastHeartbeat,
            )
        }
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
                    if (event.isDownNow(pendingFailureRepository)) {
                        databaseEventHandler.handleUptimeMonitorEvent(event)
                        eventDispatcher.dispatch(event)
                    }
                }
            }
        }
}
