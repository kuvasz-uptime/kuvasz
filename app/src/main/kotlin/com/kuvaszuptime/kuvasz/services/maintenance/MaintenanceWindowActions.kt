package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.pojos.MaintenanceWindow
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.MaintenanceWindowNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowUpdateDto
import com.kuvaszuptime.kuvasz.models.maintenance.toMaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.maintenance.validateScheduleConsistency
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.DEFAULT_PAGE_CACHE_NAME
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.STATUS_PAGES_CACHE_NAME
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import com.kuvaszuptime.kuvasz.validation.validateCategories
import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Singleton
class MaintenanceWindowActions(
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val monitorIdValidator: MonitorIdValidator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val calculator: MaintenanceWindowCalculator,
    private val scheduler: MaintenanceWindowScheduler,
    private val dslContext: DSLContext,
    private val validator: Validator,
) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMaintenanceWindows(sortedBy: SortField<*>? = null): List<MaintenanceWindowDetailsDto> =
        maintenanceWindowRepository.fetchAll(sortedBy).map { it.toDetailsDto(calculator) }

    fun getMaintenanceWindowById(maintenanceWindowId: Long): MaintenanceWindowDetailsDto =
        maintenanceWindowRepository.findById(maintenanceWindowId)
            .orThrowNotFound(maintenanceWindowId.toString())
            .toDetailsDto(calculator)

    @CacheInvalidate(DEFAULT_PAGE_CACHE_NAME, all = true)
    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = true)
    fun createMaintenanceWindow(createDto: MaintenanceWindowCreateDto): MaintenanceWindowDetailsDto {
        createDto.validateScheduleConsistency()
        // Non-existing monitors are silently dropped, but integrations must exist. The categories are only
        // normalized, not checked for existence, because they are predicates over the monitors that carry them
        val validatedMonitors = monitorIdValidator.validateMonitorIds(createDto.monitors.orEmpty())
        val validatedCategories = validateCategories(createDto.categories.orEmpty())
        val validatedIntegrations = integrationIdValidator.validateIntegrationIds(createDto.integrations.orEmpty())

        return maintenanceWindowRepository
            .returningInsert(
                createDto.toMaintenanceWindowRecord(validatedMonitors, validatedCategories, validatedIntegrations)
            )
            .also { scheduler.onWindowCreated(it) }
            .toDetailsDto(calculator)
    }

    @CacheInvalidate(DEFAULT_PAGE_CACHE_NAME, all = true)
    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = true)
    fun deleteMaintenanceWindowById(maintenanceWindowId: Long): Unit =
        maintenanceWindowRepository.findById(maintenanceWindowId)
            .orThrowNotFound(maintenanceWindowId.toString())
            .let { window ->
                maintenanceWindowRepository.deleteById(window.id)
                scheduler.onWindowDeleted(window)
            }

    @CacheInvalidate(DEFAULT_PAGE_CACHE_NAME, all = true)
    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = true)
    fun updateMaintenanceWindow(maintenanceWindowId: Long, updates: ObjectNode): MaintenanceWindowDetailsDto {
        val previous = maintenanceWindowRepository.findById(maintenanceWindowId)
            .orThrowNotFound(maintenanceWindowId.toString())

        val updated = dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existing = maintenanceWindowRepository.findById(maintenanceWindowId, txCtx)
                .orThrowNotFound(maintenanceWindowId.toString())
            val toUpdate = existing.into(MaintenanceWindow::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedWindow = objectMapper.updateValue(toUpdate, filteredUpdates)

            objectMapper.convertValue<MaintenanceWindowUpdateDto>(updatedWindow).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
                toValidate.validateScheduleConsistency()
            }
            // Filter out non-existing monitor IDs, normalize the categories, and ensure that the referenced
            // integrations exist. All three columns are NOT NULL, so an explicitly nulled one is taken as a
            // request to clear it.
            updatedWindow.monitors =
                monitorIdValidator.validateMonitorIds(updatedWindow.monitors ?: emptyArray()).toTypedArray()
            updatedWindow.categories =
                validateCategories((updatedWindow.categories ?: emptyArray()).toList()).toTypedArray()
            updatedWindow.integrations = updatedWindow.integrations ?: emptyArray()
            integrationIdValidator.validateIntegrationIds(updatedWindow.integrations)

            maintenanceWindowRepository.returningUpdate(MaintenanceWindowRecord(updatedWindow), txCtx)
        }

        scheduler.onWindowUpdated(previous, updated)

        return updated.toDetailsDto(calculator)
    }

    fun getMaintenanceWindowsExport(): List<MaintenanceWindowRecord> = maintenanceWindowRepository.fetchAll()

    private fun MaintenanceWindowRecord?.orThrowNotFound(maintenanceWindowId: String): MaintenanceWindowRecord =
        this ?: throw MaintenanceWindowNotFoundException(maintenanceWindowId)
}
