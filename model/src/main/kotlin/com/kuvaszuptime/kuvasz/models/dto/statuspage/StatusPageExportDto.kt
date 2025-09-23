package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected

@Introspected
data class StatusPageExportDto(
    val title: String,
    val slug: String,
    val customLogoUrl: String?,
    val customFaviconUrl: String?,
    val public: Boolean,
    val monitors: Set<MonitorID>,
) {
    companion object {
        fun fromStatusPageRecord(record: StatusPageRecord) =
            StatusPageExportDto(
                title = record.title,
                slug = record.slug,
                customLogoUrl = record.customLogoUrl,
                customFaviconUrl = record.customFaviconUrl,
                public = record.public,
                monitors = record.monitors.toSet(),
            )
    }
}
