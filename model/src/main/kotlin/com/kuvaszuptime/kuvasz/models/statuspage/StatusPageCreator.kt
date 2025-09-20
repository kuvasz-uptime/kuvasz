package com.kuvaszuptime.kuvasz.models.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

interface StatusPageCreator {
    @get:NotBlank(message = StatusPageValidationMessages.TITLE_NOT_BLANK)
    val title: String

    @get:NotBlank(message = StatusPageValidationMessages.SLUG_NOT_BLANK)
    @get:Pattern(regexp = Validation.SLUG_REGEX, message = StatusPageValidationMessages.SLUG_PATTERN)
    val slug: String
    val customLogoUrl: String?
    val customFaviconUrl: String?
    val public: Boolean
    val monitors: List<String>?
}

fun StatusPageCreator.toStatusPageRecord(validatedMonitors: Set<MonitorID>): StatusPageRecord =
    StatusPageRecord()
        .setTitle(title)
        .setSlug(slug)
        .setCustomLogoUrl(customLogoUrl)
        .setCustomFaviconUrl(customFaviconUrl)
        .setPublic(public)
        .setMonitors(validatedMonitors.toTypedArray())
