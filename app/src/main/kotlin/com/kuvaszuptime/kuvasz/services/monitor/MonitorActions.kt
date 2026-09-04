package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.models.MonitorCannotBeDeletedException
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.monitorId
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.validation.validator.Validator
import org.jooq.DSLContext
import org.jooq.Record
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Duration

abstract class MonitorActions<R, D : MonitorDetailsDto>(
    private val dslContext: DSLContext,
    private val appConfig: AppConfig,
    private val statusPageRepository: StatusPageRepository,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    protected val maintenanceWindowService: MaintenanceWindowService,
    private val monitorTypeSupport: MonitorTypeSupport<*, R, D>,
    private val validator: Validator,
    protected val integrationIdValidator: IntegrationIdValidator,
    private val statusPageCacheInvalidator: StatusPageCacheInvalidator,
) where R : MonitorRecord, R : Record {
    private val monitorRepository: MonitorRepository<R, D> get() = monitorTypeSupport.repository
    private val monitorType: MonitorType get() = monitorTypeSupport.monitorType

    protected val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    /**
     * Checks if it's safe to update the monitor's name or delete it at all from the status pages' perspective.
     * If the monitor is referenced by a status page that is not writable, then we cannot change its name or delete it,
     * to preserve referential integrity.
     */
    fun isMonitorChangeable(existingMonitor: R): Boolean =
        if (!appConfig.isStatusPageExternalWriteDisabled()) {
            true
        } else {
            val referencingStatusPages = statusPageRepository.getStatusPagesOfMonitor(existingMonitor.monitorId())
            referencingStatusPages.isEmpty()
        }

    fun deleteMonitorById(monitorId: Long) {
        val deletedMonitor = dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId).also { monitor ->
                if (!isMonitorChangeable(monitor)) {
                    throw MonitorCannotBeDeletedException(
                        "Monitor cannot be deleted because it is referenced by a read-only status page"
                    )
                }
                monitorRepository.deleteById(monitor.id, txCtx)
                afterDelete(monitor)
            }
        }
        announceChange(MonitorDeleteEvent(deletedMonitor.numericMonitorId()))
    }

    private fun announceChange(event: MonitorLifecycleEvent) {
        eventDispatcher.dispatch(event)
        statusPageCacheInvalidator.invalidateAllCaches()
    }

    protected fun announceCreation() {
        statusPageCacheInvalidator.invalidateAllCaches()
    }

    protected open fun afterDelete(deletedMonitor: R) = Unit

    /**
     * Applies a partial update on a monitor: only the properties present in [updates] are touched, the patched state
     * is validated as a whole, and the change is announced once it is committed.
     *
     * The patch is applied on the type's jOOQ POJO, so [updates] is keyed by the record's property names.
     *
     * @param pojoType the POJO the patch is applied on
     * @param updateDtoType the DTO the patched state is validated against
     * @param toRecord builds the record to persist out of the patched POJO
     */
    protected fun <P : Any, U : Any> updateMonitor(
        monitorId: Long,
        updates: ObjectNode,
        pojoType: Class<P>,
        updateDtoType: Class<U>,
        toRecord: (P) -> R,
    ): R = dslContext.transactionResultWithError { config ->
        val txCtx = config.dsl()
        val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
        val filteredUpdates = updates.propertyNames()
            .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                acc.set(patchAliases[fieldName] ?: fieldName, updates.get(fieldName))
            }
        val patched = objectMapper.updateValue(existingMonitor.into(pojoType), filteredUpdates)
        val updatedMonitor = toRecord(patched)
        // Check if name is present in a non-writable status page as reference
        if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
            throw ReadOnlyMonitorNameException()
        }

        checkUpdateConstraints(existingMonitor, updatedMonitor)

        validator.validate(patched.asUpdateDto(updateDtoType)).throwIfNotEmpty()
        // Validate the raw integrations from the DTO
        updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

        monitorRepository.returningUpdate(updatedMonitor, txCtx).also { saved ->
            afterUpdate(existingMonitor, saved, txCtx)
        }
    }.also { updatedMonitorRecord ->
        announceChange(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
    }

    /**
     * The properties an update DTO exposes under a different name than the column they are stored in, as
     * `API name -> record's name`. The API contract is the DTO's name, so patches arrive keyed by it, while the POJO
     * the patch is applied on is keyed by the record's - they are translated in both directions around the patch.
     */
    protected open val patchAliases: Map<String, String> = emptyMap()

    private fun <U : Any> Any.asUpdateDto(updateDtoType: Class<U>): U {
        val tree = objectMapper.valueToTree<ObjectNode>(this)
        patchAliases.forEach { (apiName, recordName) ->
            tree.get(recordName)?.let { tree.set(apiName, it) }
        }
        return objectMapper.treeToValue(tree, updateDtoType)
    }

    protected open fun checkUpdateConstraints(existingMonitor: R, updatedMonitor: R) = Unit

    protected open fun afterUpdate(existingMonitor: R, updatedMonitor: R, txCtx: DSLContext) {
        monitorTypeSupport.onUpserted(existingMonitor, updatedMonitor, txCtx)
    }

    /**
     * Looks up a monitor and calculates its historical uptime stats over the given period, then lets the caller
     * assemble the type specific statistics on top of them.
     */
    protected fun <T> withUptimeHistory(
        monitorId: Long,
        period: Duration,
        block: (monitor: R, uptimeHistory: HistoricalUptimeStatsDto) -> T,
    ): T =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                block(monitor, statCalculator.calculateHistoricalUptimeStats(monitorType, period, monitor.id))
            }

    /**
     * Collects the status page data of the enabled monitors, calculating everything that is common across the monitor
     * types, and letting the caller enrich the result with the type specific details.
     */
    protected fun <S : StatusPageMonitorDetailsDto> buildStatusPageData(
        period: Duration,
        monitorIds: List<MonitorID>?,
        buildDetails: (monitor: D, uptime: StatusPageUptimeData) -> S,
    ): List<S> {
        val monitorNames = monitorIds?.filter { it.type == monitorType }?.map { it.name }
        val enabledMonitors = monitorRepository.fetchAllWithDetails(enabled = true, monitorNames = monitorNames)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(enabledMonitors.map { it.monitorId() })
        val overviewsByMonitor = statCalculator.calculateUptimeOverviews(
            monitorType = monitorType,
            period = period,
            monitorIds = enabledMonitors.map { it.id },
        )

        return enabledMonitors.map { monitor ->
            val overview = overviewsByMonitor.getValue(monitor.id)
            val uptimeData = StatusPageUptimeData(
                uptimeRatio = overview.uptimeRatio,
                uptimeStatusHistory = overview.statusHistory,
                inMaintenance = windowsByMonitor[monitor.monitorId()].orEmpty().any { it.active },
            )
            buildDetails(monitor, uptimeData)
        }
    }

    fun R?.orThrowNotFound(monitorId: Long): R = this ?: throw MonitorNotFoundException(monitorId)

    protected data class StatusPageUptimeData(
        val uptimeRatio: Double?,
        val uptimeStatusHistory: List<StatusHistoryDto>,
        val inMaintenance: Boolean,
    )
}
