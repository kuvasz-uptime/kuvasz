package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import java.time.Duration

private const val CHART_MIN_HEIGHT_STYLE = "min-height: 240px;"

private val DEFAULT_METRICS_PERIOD: Duration = Duration.ofDays(UIDefaults.MONITOR_METRICS_PERIOD_DAYS)

/**
 * A single card of a metrics row, rendered only when the Alpine.js property behind [propertyName] has a value.
 * [unit] is appended to the value as-is, so it has to carry its own leading space when it needs one.
 */
internal fun FlowContent.metricStatCard(
    propertyName: String,
    label: String,
    unit: String,
    valueExpression: String = propertyName,
) {
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
                        xText("$valueExpression + '$unit'")
                    }
                }
            }
        }
    }
}

/**
 * The stat cards of a metric, one for each of [cards] (a stat's property name and its label). [propertyPrefix] is the
 * Alpine.js path of the stats object, [propertySuffix] the name of the metric within it, e.g. `LatencyInMs` for
 * `latencyStats.p90LatencyInMs`. [labelPrefix] names the measurement when a row holds more than one.
 */
private fun FlowContent.metricStatCards(
    propertyPrefix: String,
    propertySuffix: String,
    unit: String,
    cards: List<Pair<String, String>>,
    labelPrefix: String? = null,
    valueExpression: (String) -> String = { it },
) {
    cards.forEach { (property, label) ->
        val propertyName = "$propertyPrefix.$property$propertySuffix"
        metricStatCard(
            propertyName = propertyName,
            label = labelPrefix?.let { "$it - $label" } ?: label,
            unit = unit,
            valueExpression = valueExpression(propertyName),
        )
    }
}

private fun FlowContent.metricStatRow(block: DIV.() -> Unit) {
    div {
        classes(ROW, ROW_CARDS, MB_3)
        block()
    }
}

private fun averageMinMaxCards() = listOf("average" to Messages.average(), "min" to "Min", "max" to "Max")
private val PERCENTILE_CARDS = listOf("p90" to "P90", "p95" to "P95", "p99" to "P99")

/**
 * The container's CPU and memory in one row: three cards each, which is exactly the six a row fits from `md` up.
 *
 * The CPU figures stay fractional, because a container idling below a whole percent would otherwise read as a flat
 * zero, and they go above 100% on several cores. The memory is stored in bytes and shown in MiB, the unit an
 * operator sizing a container actually thinks in. Each card names its measurement, since the two sit side by side.
 */
internal fun FlowContent.dockerResourceMetricCards() =
    metricStatRow {
        metricStatCards(
            propertyPrefix = "lastResponse?.cpuStats?",
            propertySuffix = "CpuUsagePercentage",
            unit = "%",
            cards = averageMinMaxCards(),
            labelPrefix = Messages.cpu(),
        )
        metricStatCards(
            propertyPrefix = "lastResponse?.memoryStats?",
            propertySuffix = "MemoryUsageBytes",
            unit = " MiB",
            cards = averageMinMaxCards(),
            labelPrefix = Messages.memory(),
            valueExpression = { "bytesToMib($it)" },
        )
    }

internal fun FlowContent.latencyMetricCards() =
    metricStatRow {
        metricStatCards(
            propertyPrefix = "lastResponse?.latencyStats?",
            propertySuffix = "LatencyInMs",
            unit = " ms",
            cards = averageMinMaxCards() + PERCENTILE_CARDS,
        )
    }

internal fun FlowContent.packetLossMetricCards() =
    metricStatRow {
        metricStatCards(
            propertyPrefix = "lastResponse?.packetLossStats?",
            propertySuffix = "PacketLossPercentage",
            unit = "%",
            cards = averageMinMaxCards() + PERCENTILE_CARDS,
        )
    }

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

/**
 * The heading of a metrics block with the period selector and the auto-refresh toggle next to it
 */
private fun FlowContent.metricsSectionHeading() {
    div {
        classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_3)
        h2 {
            classes(MB_0)
            testId("metrics-block-title")
            +Messages.metrics()
        }
        div {
            classes(MS_AUTO, D_FLEX, ALIGN_ITEMS_CENTER)
            div {
                classes(ME_2)
                periodSelector(selected = DEFAULT_METRICS_PERIOD) {
                    testId("metrics-period-selector")
                    xModel("period")
                }
            }
            metricsAutoRefreshToggle()
        }
    }
}

internal fun FlowContent.metricsChartCard(chartElementId: String) {
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
 * The root of a metrics block: the Alpine.js component that polls the metrics (and the incidents) of the monitor in the
 * selected period, rendering the heading of the block and feeding every card and chart rendered by [content].
 */
internal fun FlowContent.monitorMetricsBlock(
    typeUiConfig: MonitorTypeUiConfig,
    monitorId: Long,
    isMonitorEnabled: Boolean,
    uptimeCheckInterval: Int,
    content: FlowContent.() -> Unit,
) {
    div {
        xData(
            """${typeUiConfig.alpineComponent("MetricsBlock")}(
            |$monitorId,
            |$isMonitorEnabled,
            |$uptimeCheckInterval,
            |{
            |noData: "${Messages.latencyChartNoData()}",
            |incidentStarted: "${Messages.chartIncidentStarted()}",
            |incidentResolved: "${Messages.chartIncidentResolved()}",
            |latency: "${Messages.latencyBlockTitle()}",
            |packetLoss: "${Messages.packetLossBlockTitle()}",
            |cpuUsage: "${Messages.dockerCpuUsageBlockTitle()}",
            |memoryUsage: "${Messages.dockerMemoryUsageBlockTitle()}",
            |memoryLimit: "${Messages.dockerMemoryLimit()}",
            |},
            |"$DEFAULT_METRICS_PERIOD"
            |)
            """.trimMargin()
        )
        xOn("monitor-disabled.window", "isAutoRefreshEnabled = false")
        metricsSectionHeading()
        div {
            classes(POSITION_RELATIVE)
            content()
            loadingOverlay(xShowIf = "isPeriodLoading", overlayTestId = "metrics-loading-overlay")
        }
    }
}
