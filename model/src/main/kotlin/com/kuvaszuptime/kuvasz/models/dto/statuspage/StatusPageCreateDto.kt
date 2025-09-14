package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class StatusPageCreateDto(
    @param:Schema(description = StatusPageDocs.TITLE, required = false, defaultValue = StatusPageDefaults.TITLE)
    override val title: String,
    @param:Schema(description = StatusPageDocs.SLUG, required = true)
    override val slug: String,
    @param:Schema(
        description = StatusPageDocs.ENABLED,
        required = false,
        defaultValue = StatusPageDefaults.CUSTOM_PAGE_ENABLED.toString()
    )
    override val enabled: Boolean = StatusPageDefaults.CUSTOM_PAGE_ENABLED,
    @param:Schema(description = StatusPageDocs.MONITORS, required = false)
    override val monitors: List<String>? = emptyList(),
) : StatusPageCreator
