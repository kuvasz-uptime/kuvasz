package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.theme.ThemeBase
import io.micronaut.core.annotation.Introspected

@Introspected
data class StatusPageExportDto(
    val title: String,
    val slug: String,
    val customLogoUrl: String?,
    val customFaviconUrl: String?,
    val public: Boolean,
    val monitors: Set<MonitorID>,
    val categories: Set<String> = emptySet(),
    val displayCategories: Boolean = StatusPageDefaults.DISPLAY_CATEGORIES,
    // Missing from the exports made before the gray palette could be picked
    val themeBase: ThemeBase = ThemeBase.DEFAULT,
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
                categories = record.categories.toSet(),
                displayCategories = record.displayCategories,
                themeBase = record.themeBaseOrDefault,
            )
    }
}
