package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class StatusPageImportAdapter(dto: StatusPageExportDto) : StatusPageCreator {
    override val title: String = dto.title
    override val slug: String = dto.slug
    override val customLogoUrl: String? = dto.customLogoUrl
    override val customFaviconUrl: String? = dto.customFaviconUrl
    override val public: Boolean = dto.public
    override val monitors: List<String> = dto.monitors.map { it.toString() }
}
