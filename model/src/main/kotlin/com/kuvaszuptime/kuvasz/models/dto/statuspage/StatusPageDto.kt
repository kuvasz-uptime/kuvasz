package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class StatusPageDto(
    @param:Schema(description = StatusPageDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = StatusPageDocs.TITLE, required = true)
    val title: String,
    @param:Schema(description = StatusPageDocs.SLUG, required = true)
    val slug: String,
    @param:Schema(description = StatusPageDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = StatusPageDocs.MONITORS, required = true)
    val monitors: Set<MonitorID>,
    @param:Schema(description = StatusPageDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = StatusPageDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromStatusPageRecord(record: StatusPageRecord) =
            StatusPageDto(
                id = record.id,
                title = record.title,
                slug = record.slug,
                enabled = record.enabled,
                monitors = record.monitors.toSet(),
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
            )
    }
}
