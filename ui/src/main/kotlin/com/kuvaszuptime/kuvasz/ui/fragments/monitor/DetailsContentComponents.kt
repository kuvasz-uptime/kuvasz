package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.models.uptimeIncidentType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

private val DETAILS_REFRESH_INTERVAL = 15.seconds

/** A block whose content is loaded and kept up to date by htmx. */
internal fun FlowContent.autoRefreshedBlock(
    elementId: String,
    path: String,
    cssClasses: Set<CSSClass> = emptySet(),
) {
    div {
        if (cssClasses.isNotEmpty()) {
            classes(cssClasses)
        }
        id = elementId
        hx {
            get(path)
            trigger {
                load()
                every(DETAILS_REFRESH_INTERVAL)
            }
            onSwapReinitTooltips()
            swap(HtmxAttrs.Swap.innerHTML)
        }
    }
}

/**
 * The incidents of [type] of a monitor in the last few days, page by page, refreshed by htmx, under a heading telling
 * the reader what period they cover. The state of the list is its own, it's not put into the URL of the details page,
 * but its pages, and the heading, link to the incidents page filtered the same way.
 */
internal fun FlowContent.monitorIncidentsBlock(elementId: String, type: IncidentType, monitorId: Long) {
    val query = monitorIncidentListQuery(params = { emptyList() }, type, monitorId)
    h3 {
        +Messages.incidents()
        span {
            classes(BADGE)
            +Messages.lastX(ListDefaults.INCIDENT_PERIOD.formatAsSimpleInterval())
        }
        a(href = query.url) {
            classes(MS_2, FS_5)
            testId("all-incidents-link")
            +Messages.viewAllIncidents()
        }
    }
    div {
        xData("listQuery(${query.alpineState()})")
        pagedList(
            query = query,
            listElementId = elementId,
            fragmentPath = monitorIncidentListFragmentPath(type, monitorId),
            refreshEvent = null,
            refreshInterval = DETAILS_REFRESH_INTERVAL,
            listClasses = setOf(ROW, ROW_CARDS, MB_3),
        )
    }
}

/**
 * The details page content of a monitor. Every type opens with its uptime summary and the incidents that happened in
 * it, then adds whatever else it tracks in [extraBlocks].
 */
internal fun FlowContent.monitorDetailsContent(
    typeUiConfig: MonitorTypeUiConfig,
    monitor: MonitorDetailsDto,
    uptimeSummary: FlowContent.() -> Unit,
    extraBlocks: FlowContent.() -> Unit = {},
) {
    div {
        id = "${typeUiConfig.slug}-monitor-details-content"
        // Uptime summary
        h2 {
            testId("uptime-block-title")
            +Messages.uptimeBlockTitle()
        }
        uptimeSummary()
        // Uptime incidents
        monitorIncidentsBlock(
            elementId = "${typeUiConfig.slug}-monitor-details-incidents",
            type = typeUiConfig.type.uptimeIncidentType,
            monitorId = monitor.id,
        )
        extraBlocks()
    }
}
