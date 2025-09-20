package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class StatusPageCreateDto(
    @param:Schema(description = StatusPageDocs.TITLE, required = true)
    override val title: String,
    @param:Schema(description = StatusPageDocs.SLUG, required = true)
    override val slug: String,
    @param:Schema(description = StatusPageDocs.CUSTOM_LOGO_URL, required = false)
    override val customLogoUrl: String? = null,
    @param:Schema(description = StatusPageDocs.CUSTOM_FAVICON_URL, required = false)
    override val customFaviconUrl: String? = null,
    @param:Schema(
        description = StatusPageDocs.PUBLIC,
        required = false,
        defaultValue = StatusPageDefaults.CUSTOM_PAGE_PUBLIC.toString()
    )
    override val public: Boolean = StatusPageDefaults.CUSTOM_PAGE_PUBLIC,
    @param:Schema(description = StatusPageDocs.MONITORS, required = false)
    override val monitors: List<String>? = emptyList(),
) : StatusPageCreator
