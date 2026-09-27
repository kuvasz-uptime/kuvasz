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
 * The average/min/max/percentile cards of a metrics row. [propertyPrefix] is the Alpine.js path of the stats object,
 * [propertySuffix] the name of the metric within it, e.g. `LatencyInMs` for `latencyStats.p90LatencyInMs`.
 */
private fun FlowContent.metricStatCards(
    propertyPrefix: String,
    propertySuffix: String,
    unit: String,
    extraCards: List<Pair<String, String>> = PERCENTILE_CARDS,
    valueExpression: (String) -> String = { it },
) {
    div {
        classes(ROW, ROW_CARDS, MB_3)
        val averageProperty = "$propertyPrefix.average$propertySuffix"
        metricStatCard(
            propertyName = averageProperty,
            label = Messages.average(),
            unit = unit,
            valueExpression = valueExpression(averageProperty),
        )
        (MIN_MAX_CARDS + extraCards).forEach { (property, label) ->
            val propertyName = "$propertyPrefix.$property$propertySuffix"
            metricStatCard(
                propertyName = propertyName,
                label = label,
                unit = unit,
                valueExpression = valueExpression(propertyName),
            )
        }
    }
}

private val MIN_MAX_CARDS = listOf("min" to "Min", "max" to "Max")
private val PERCENTILE_CARDS = listOf("p90" to "P90", "p95" to "P95", "p99" to "P99")

/**
 * The container's CPU and memory in one row: three cards each, which is exactly the six a row fits from `md` up.
 *
 * The CPU figures stay fractional, because a container idling below a whole percent would otherwise read as a flat
 * zero, and they go above 100% on several cores. The memory is stored in bytes and shown in MiB, the unit an
 * operator sizing a container actually thinks in. Each card names its measurement, since the two sit side by side.
 */
internal fun FlowContent.dockerResourceMetricCards() {
    div {
        classes(ROW, ROW_CARDS, MB_3)
        resourceCards(
            propertyPrefix = "lastResponse?.cpuStats?",
            propertySuffix = "CpuUsagePercentage",
            labelPrefix = Messages.cpu(),
            unit = "%",
        )
        resourceCards(
            propertyPrefix = "lastResponse?.memoryStats?",
            propertySuffix = "MemoryUsageBytes",
            labelPrefix = Messages.memory(),
            unit = " MiB",
            valueExpression = { "bytesToMib($it)" },
        )
    }
}

private fun FlowContent.resourceCards(
    propertyPrefix: String,
    propertySuffix: String,
    labelPrefix: String,
    unit: String,
    valueExpression: (String) -> String = { it },
) {
    (listOf("average" to Messages.average()) + MIN_MAX_CARDS).forEach { (property, label) ->
        val propertyName = "$propertyPrefix.$property$propertySuffix"
        metricStatCard(
            propertyName = propertyName,
            label = "$labelPrefix - $label",
            unit = unit,
            valueExpression = valueExpression(propertyName),
        )
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
