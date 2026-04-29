package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.cache.annotation.Cacheable
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class StatusPageDataActions(
    private val statusPageRepository: StatusPageRepository,
    private val monitorDataProviders: List<StatusPageMonitorDataProvider>,
    private val defaultStatusPageConfig: DefaultStatusPageConfig,
) {
    companion object {
        const val STATUS_PAGES_CACHE_NAME = "status-pages"
        const val DEFAULT_PAGE_CACHE_NAME = "default-status-page"
        private const val DEFAULT_METRICS_PERIOD = "P30D"
    }

    @Cacheable(DEFAULT_PAGE_CACHE_NAME)
    fun getCachedDefaultStatusPageData() = getDefaultStatusPageData()

    fun getDefaultStatusPageData(): StatusPageDataDto {
        val monitors = monitorDataProviders.flatMap { provider ->
            provider.getStatusPageDataOfEnabledMonitors(
                period = Duration.parse(DEFAULT_METRICS_PERIOD),
                monitorIds = null,
            )
        }

        return StatusPageDataDto(
            title = defaultStatusPageConfig.title,
            customLogoUrl = defaultStatusPageConfig.customLogoUrl,
            customFaviconUrl = defaultStatusPageConfig.customFaviconUrl,
            systemStatus = calculateSystemStatus(monitors),
            generatedAt = getCurrentTimestamp(),
            monitors = monitors,
        )
    }

    private fun calculateSystemStatus(monitors: List<StatusPageMonitorDetailsDto>) =
        if (monitors.isEmpty()) {
            SystemStatus.PENDING
        } else {
            val monitorStatusMap = monitors.groupBy { it.uptimeStatus }
            val monitorCnt = monitors.size
            when {
                monitorStatusMap[UptimeStatus.UP]?.size == monitorCnt -> SystemStatus.OPERATIONAL
                monitorStatusMap[UptimeStatus.DOWN]?.size == monitorCnt -> SystemStatus.MAJOR_OUTAGE
                !monitorStatusMap[UptimeStatus.UP].isNullOrEmpty() &&
                    !monitorStatusMap[UptimeStatus.DOWN].isNullOrEmpty() -> SystemStatus.PARTIAL_OUTAGE

                else -> SystemStatus.PENDING
            }
        }

    @Cacheable(STATUS_PAGES_CACHE_NAME)
    fun getCachedStatusPageData(statusPageId: Long) = getStatusPageData(statusPageId)

    fun getStatusPageData(statusPageId: Long): StatusPageDataDto {
        val statusPage = statusPageRepository.findById(statusPageId).orThrowNotFound(statusPageId.toString())
        val monitors = monitorDataProviders.flatMap { provider ->
            provider.getStatusPageDataOfEnabledMonitors(
                period = Duration.parse(DEFAULT_METRICS_PERIOD),
                monitorIds = statusPage.monitors?.toList(),
            )
        }

        return StatusPageDataDto(
            title = statusPage.title,
            customLogoUrl = statusPage.customLogoUrl,
            customFaviconUrl = statusPage.customFaviconUrl,
            generatedAt = getCurrentTimestamp(),
            systemStatus = calculateSystemStatus(monitors),
            monitors = monitors,
        )
    }
}
