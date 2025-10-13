package com.kuvaszuptime.kuvasz.services.statuspage

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField

@Singleton
class StatusPageActions(
    private val statusPageRepository: StatusPageRepository,
    private val monitorIdValidator: MonitorIdValidator,
    private val dslContext: DSLContext,
    private val validator: Validator,
) {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModules(JavaTimeModule())

    fun getStatusPages(
        public: Boolean?,
        sortedBy: SortField<*>? = null,
    ): List<StatusPageDto> =
        statusPageRepository.fetchAll(public, sortedBy)
            .map { StatusPageDto.fromStatusPageRecord(it) }

    fun getStatusPageById(statusPageId: Long): StatusPageDto =
        statusPageRepository.findById(statusPageId)?.let { StatusPageDto.fromStatusPageRecord(it) }
            ?: throw StatusPageNotFoundException(statusPageId)

    fun getStatusPageBySlug(slug: String, public: Boolean? = null): StatusPageDto? =
        statusPageRepository.findBySlug(slug, public = public)?.let { StatusPageDto.fromStatusPageRecord(it) }

    fun createStatusPage(statusPageCreateDto: StatusPageCreateDto): StatusPageRecord {
        // Validate the raw monitors from the DTO
        val validatedMonitors =
            monitorIdValidator.validateMonitorIds(statusPageCreateDto.monitors.orEmpty())

        return statusPageRepository.returningInsert(statusPageCreateDto.toStatusPageRecord(validatedMonitors))
    }

    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun deleteStatusPageById(statusPageId: Long): Unit =
        statusPageRepository.findById(statusPageId)
            .orThrowNotFound(statusPageId)
            .let { statusPage ->
                statusPageRepository.deleteById(statusPage.id)
            }

    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun updateStatusPage(statusPageId: Long, updates: ObjectNode): StatusPageRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingStatusPage = statusPageRepository.findById(statusPageId, txCtx).orThrowNotFound(statusPageId)
            val toUpdate = existingStatusPage.into(StatusPage::class.java)
            val filteredUpdates = updates.fieldNames().asSequence()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedStatusPage = objectMapper.updateValue(toUpdate, filteredUpdates)

            objectMapper.convertValue<StatusPageUpdateDto>(updatedStatusPage).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Filter out non-existing monitor IDs before updating
            if (updatedStatusPage.monitors != null) {
                updatedStatusPage.monitors =
                    monitorIdValidator.validateMonitorIds(updatedStatusPage.monitors).toTypedArray()
            }

            statusPageRepository.returningUpdate(StatusPageRecord(updatedStatusPage), txCtx)
        }

    private fun StatusPageRecord?.orThrowNotFound(statusPageId: Long): StatusPageRecord =
        this ?: throw StatusPageNotFoundException(statusPageId)

    fun getStatusPagesExport(): List<StatusPageRecord> = statusPageRepository.fetchAll()
}
