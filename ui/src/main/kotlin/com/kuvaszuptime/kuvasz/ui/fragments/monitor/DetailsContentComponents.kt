package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

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
                every(15.seconds)
            }
            onSwapReinitTooltips()
            swap(HtmxAttrs.Swap.innerHTML)
        }
    }
}

/** The heading of an incidents block, telling the reader what period the incidents below it cover. */
internal fun FlowContent.incidentsHeading() {
    h3 {
        +Messages.incidents()
        span {
            classes(BADGE)
            +Messages.lastX(Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS).formatAsSimpleInterval())
        }
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
        incidentsHeading()
        autoRefreshedBlock(
            elementId = "${typeUiConfig.slug}-monitor-details-incidents",
            path = typeUiConfig.fragmentPath("details-uptime-incidents/${monitor.id}"),
            cssClasses = setOf(ROW, ROW_CARDS, MB_3),
        )
        extraBlocks()
    }
}
