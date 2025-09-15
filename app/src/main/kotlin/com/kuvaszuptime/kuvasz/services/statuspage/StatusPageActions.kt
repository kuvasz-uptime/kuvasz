package com.kuvaszuptime.kuvasz.services.statuspage

import arrow.core.getOrHandle
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.StatusPage
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.StatusPageNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.models.statuspage.toStatusPageRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.validation.MonitorIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import java.time.LocalDate

@Singleton
class StatusPageActions(
    private val statusPageRepository: StatusPageRepository,
    private val monitorIdValidator: MonitorIdValidator,
    private val dslContext: DSLContext,
    private val validator: Validator,
) {
    companion object {
        private const val CACHE_NAME = "status-pages"
    }

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModules(JavaTimeModule())

    fun getStatusPages(
        enabled: Boolean?,
        sortedBy: SortField<*>? = null,
    ): List<StatusPageDto> =
        statusPageRepository.fetchAll(enabled, sortedBy)
            .map { StatusPageDto.fromStatusPageRecord(it) }

    fun getStatusPage(statusPageId: Long): StatusPageDto =
        statusPageRepository.findById(statusPageId)?.let { StatusPageDto.fromStatusPageRecord(it) }
            ?: throw StatusPageNotFoundException(statusPageId)

    fun createStatusPage(statusPageCreateDto: StatusPageCreateDto): StatusPageRecord {
        // Validate the raw monitors from the DTO
        val validatedMonitors =
            monitorIdValidator.validateMonitorIds(statusPageCreateDto.monitors.orEmpty())

        return statusPageRepository
            .returningInsert(statusPageCreateDto.toStatusPageRecord(validatedMonitors))
            .getOrHandle { persistenceError -> throw persistenceError }
    }

    @CacheInvalidate(CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun deleteStatusPageById(statusPageId: Long): Unit =
        statusPageRepository.findById(statusPageId)
            .orThrowNotFound(statusPageId)
            .let { statusPage ->
                statusPageRepository.deleteById(statusPage.id)
            }

    @CacheInvalidate(CACHE_NAME, all = false, parameters = ["statusPageId"])
    fun updateStatusPage(statusPageId: Long, updates: ObjectNode): StatusPageRecord =
        try {
            dslContext.transactionResult { config ->
                val txCtx = config.dsl()
                statusPageRepository.findById(statusPageId, txCtx)?.let { existingStatusPage ->
                    val toUpdate = existingStatusPage.into(StatusPage::class.java)
                    val filteredUpdates = updates.fieldNames().asSequence()
                        .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                            acc.set(fieldName, updates.get(fieldName))
                        }
                    val updatedStatusPage = objectMapper.updateValue(toUpdate, filteredUpdates)

                    objectMapper.convertValue<StatusPageUpdateDto>(updatedStatusPage).let { toValidate ->
                        validator.validate(toValidate).throwIfNotEmpty()
                    }
                    // Validate the raw monitors from the DTO
                    updatedStatusPage.monitors?.let { monitorIdValidator.validateMonitorIds(it) }

                    statusPageRepository.returningUpdate(StatusPageRecord(updatedStatusPage), txCtx).fold(
                        { persistenceError -> throw persistenceError },
                        { updatedStatusPageFromDb -> updatedStatusPageFromDb }
                    )
                }
            }.orThrowNotFound(statusPageId)
        } catch (ex: DataAccessException) {
            // Cause is encapsulated in the DataAccessException inside a transaction, so we need to unwrap it again here
            // because we're interested in the DuplicationErrors on the call site
            throw ex.cause ?: ex
        }

    private fun StatusPageRecord?.orThrowNotFound(statusPageId: Long): StatusPageRecord =
        this ?: throw StatusPageNotFoundException(statusPageId)

    fun getStatusPagesExport(): List<StatusPageRecord> = statusPageRepository.fetchAll()

    // TODO implement real data fetching
    @Suppress("MagicNumber", "UnusedParameter")
    @Cacheable(CACHE_NAME)
    fun getStatusPageData(statusPageId: Long): StatusPageDataDto = StatusPageDataDto(
        title = "Example status page",
        period = "P30D",
        systemStatus = SystemStatus.OPERATIONAL,
        monitors = listOf(
            StatusPageMonitorDetailsDto(
                name = "Example monitor",
                averageLatencyInMs = 123,
                uptimeRatio = 99.95,
                uptimeStatus = UptimeStatus.UP,
                uptimeStatusHistory = listOf(
                    StatusPageMonitorDetailsDto.StatusHistoryDto(
                        date = LocalDate.now().minusDays(3),
                        outageCnt = 2,
                    ),
                    StatusPageMonitorDetailsDto.StatusHistoryDto(
                        date = LocalDate.now().minusDays(2),
                        outageCnt = 0,
                    ),
                    StatusPageMonitorDetailsDto.StatusHistoryDto(
                        date = LocalDate.now().minusDays(1),
                        outageCnt = 1,
                    ),
                    StatusPageMonitorDetailsDto.StatusHistoryDto(
                        date = LocalDate.now(),
                        outageCnt = 0,
                    ),
                ),
            ),
        )
    )
}
