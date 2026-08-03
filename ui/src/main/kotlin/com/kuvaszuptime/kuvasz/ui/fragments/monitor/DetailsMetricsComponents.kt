package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

private const val CHART_MIN_HEIGHT_STYLE = "min-height: 240px;"

/**
 * A single card of a metrics row, rendered only when the Alpine.js property behind [propertyName] has a value.
 * [unit] is appended to the value as-is, so it has to carry its own leading space when it needs one.
 */
internal fun FlowContent.metricStatCard(propertyName: String, label: String, unit: String) {
    templateTag {
        xIf("$propertyName != null")
        div {
            classes(COL_MD_2, COL_SM_4, COL_6)
            div {
                classes(CARD)
                div {
                    classes(CARD_BODY)
                    div {
                        classes(SUBHEADER)
                        +label
                    }
                    h4 {
                        classes(M_0)
                        xText("$propertyName + '$unit'")
                    }
                }
            }
        }
    }
}

/**
 * The average/min/max/percentile cards of a metrics row. [propertyPrefix] is the Alpine.js path of the stats object,
 * [propertySuffix] the name of the metric within it, e.g. `LatencyInMs` for `latencyStats.p90LatencyInMs`.
 */
private fun FlowContent.metricStatCards(
    propertyPrefix: String,
    propertySuffix: String,
    unit: String,
) {
    div {
        classes(ROW, ROW_CARDS, MB_3)
        metricStatCard(
            propertyName = "$propertyPrefix.average$propertySuffix",
            label = Messages.latencyAverage(),
            unit = unit,
        )
        listOf("min" to "Min", "max" to "Max", "p90" to "P90", "p95" to "P95", "p99" to "P99")
            .forEach { (property, label) ->
                metricStatCard(propertyName = "$propertyPrefix.$property$propertySuffix", label = label, unit = unit)
            }
    }
}

internal fun FlowContent.latencyMetricCards() =
    metricStatCards(
        propertyPrefix = "lastResponse?.latencyStats?",
        propertySuffix = "LatencyInMs",
        unit = " ms",
    )

internal fun FlowContent.packetLossMetricCards() =
    metricStatCards(
        propertyPrefix = "lastResponse?.packetLossStats?",
        propertySuffix = "PacketLossPercentage",
        unit = "%",
    )

internal fun FlowContent.metricsAutoRefreshToggle() {
    label {
        classes(FORM_CHECK, FORM_SWITCH, MB_0)
        input(type = InputType.checkBox, name = "autoRefreshToggle") {
            classes(FORM_CHECK_INPUT)
            xModel("isAutoRefreshEnabled")
        }
        span {
            classes(FORM_CHECK_LABEL)
            icon(Icon.REFRESH)
        }
    }
}

/** The metrics heading with the auto-refresh toggle next to it, for the types that have more than one metrics row. */
internal fun FlowContent.metricsSectionHeading(statPeriodInHours: Long) {
    div {
        classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_3)
        h2 {
            classes(MB_0)
            +Messages.metrics()
            span {
                classes(BADGE)
                +Messages.lastXHours(statPeriodInHours)
            }
        }
        div {
            classes(MS_AUTO)
            metricsAutoRefreshToggle()
        }
    }
}

/**
 * The card holding a metrics chart. [withAutoRefreshToggle] puts the toggle into the card's header, which is where the
 * types without a [metricsSectionHeading] of their own carry it.
 */
internal fun FlowContent.metricsChartCard(chartElementId: String, withAutoRefreshToggle: Boolean = false) {
    div {
        classes(ROW, ROW_CARDS, MB_3)
        div {
            classes(COL_12)
            div {
                classes(CARD)
                div {
                    classes(CARD_HEADER)
                    h3 {
                        classes(CARD_TITLE)
                        +Messages.recentMeasurements()
                    }
                    if (withAutoRefreshToggle) {
                        div {
                            classes(CARD_ACTIONS, BTN_ACTIONS)
                            metricsAutoRefreshToggle()
                        }
                    }
                }
                div {
                    classes(CARD_BODY)
                    div {
                        id = chartElementId
                        style = CHART_MIN_HEIGHT_STYLE
                    }
                }
            }
        }
    }
}

/**
 * The root of a metrics block: the Alpine.js component that polls the metrics of the monitor and feeds every card and
 * chart rendered by [content].
 */
internal fun FlowContent.monitorMetricsBlock(
    typeUiConfig: MonitorTypeUiConfig,
    monitorId: Long,
    isMonitorEnabled: Boolean,
    uptimeCheckInterval: Int,
    statPeriodInHours: Long,
    content: FlowContent.() -> Unit,
) {
    div {
        xData(
            """${typeUiConfig.alpineComponent("MetricsBlock")}(
            |$monitorId,
            |$isMonitorEnabled,
            |$uptimeCheckInterval,
            |"${Messages.latencyChartNoData()}",
            |$statPeriodInHours
            |)
            """.trimMargin()
        )
        xOn("monitor-disabled.window", "isAutoRefreshEnabled = false")
        content()
    }
}
