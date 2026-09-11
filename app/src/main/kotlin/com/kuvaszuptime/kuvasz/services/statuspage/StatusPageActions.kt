package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.pojos.StatusPage
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.StatusPageNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import com.kuvaszuptime.kuvasz.models.statuspage.toStatusPageRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.STATUS_PAGES_CACHE_NAME
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
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
class StatusPageActions(
    private val statusPageRepository: StatusPageRepository,
    private val monitorIdValidator: MonitorIdValidator,
    private val dslContext: DSLContext,
    private val validator: Validator,
) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getStatusPages(
        public: Boolean?,
        sortedBy: SortField<*>? = null,
    ): List<StatusPageDto> =
        statusPageRepository.fetchAll(public, sortedBy)
            .map { StatusPageDto.fromStatusPageRecord(it) }

    fun getStatusPageById(statusPageId: Long): StatusPageDto =
        statusPageRepository.findById(statusPageId).orThrowNotFound(statusPageId.toString())
            .let { StatusPageDto.fromStatusPageRecord(it) }

    fun getStatusPageBySlug(slug: String, public: Boolean? = null): StatusPageDto =
        statusPageRepository.findBySlug(slug, public = public).orThrowNotFound(slug)
            .let { StatusPageDto.fromStatusPageRecord(it) }

    fun createStatusPage(statusPageCreateDto: StatusPageCreateDto): StatusPageRecord {
        // Validate the raw monitors from the DTO. The categories are only normalized, not checked for existence,
        // because they are predicates over the monitors that carry them, see validateCategories
        val validatedMonitors =
            monitorIdValidator.validateMonitorIds(statusPageCreateDto.monitors.orEmpty())
        val validatedCategories = validateCategories(statusPageCreateDto.categories.orEmpty())

        return statusPageRepository.returningInsert(
            statusPageCreateDto.toStatusPageRecord(validatedMonitors, validatedCategories)
        )
    }

    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun deleteStatusPageById(statusPageId: Long): Unit =
        statusPageRepository.findById(statusPageId)
            .orThrowNotFound(statusPageId.toString())
            .let { statusPage ->
                statusPageRepository.deleteById(statusPage.id)
            }

    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun updateStatusPage(statusPageId: Long, updates: ObjectNode): StatusPageRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingStatusPage =
                statusPageRepository.findById(statusPageId, txCtx).orThrowNotFound(statusPageId.toString())
            val toUpdate = existingStatusPage.into(StatusPage::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedStatusPage = objectMapper.updateValue(toUpdate, filteredUpdates)

            objectMapper.convertValue<StatusPageUpdateDto>(updatedStatusPage).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Filter out non-existing monitor IDs and normalize the categories before updating. Both columns are
            // NOT NULL, so an explicitly nulled selector is taken as a request to clear it.
            updatedStatusPage.monitors =
                monitorIdValidator.validateMonitorIds(updatedStatusPage.monitors ?: emptyArray()).toTypedArray()
            updatedStatusPage.categories =
                validateCategories((updatedStatusPage.categories ?: emptyArray()).toList()).toTypedArray()

            statusPageRepository.returningUpdate(StatusPageRecord(updatedStatusPage), txCtx)
        }

    fun getStatusPagesExport(): List<StatusPageRecord> = statusPageRepository.fetchAll()
}

fun StatusPageRecord?.orThrowNotFound(statusPageId: String): StatusPageRecord =
    this ?: throw StatusPageNotFoundException(statusPageId)
