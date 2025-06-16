package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

private fun FlowContent.latencyMetricCard(propertyName: String, label: String) {
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
                        xText("$propertyName + ' ms'")
                    }
                }
            }
        }
    }
}

internal fun FlowContent.detailsLatencyBlock(monitor: MonitorDetailsDto) {
    div {
        xData(
            """latencyBlock(
            |${monitor.id}, 
            |${monitor.enabled}, 
            |${monitor.uptimeCheckInterval}, 
            |"${Messages.latencyChartNoData()}"
            |)
            """.trimMargin()
        )
        xOn("monitor-disabled.window", "isAutoRefreshEnabled = false")

        div {
            classes(ROW, ROW_CARDS, MB_3)
            latencyMetricCard(propertyName = "lastResponse?.averageLatencyInMs", label = Messages.latencyAverage())
            latencyMetricCard(propertyName = "lastResponse?.minLatencyInMs", label = "Min")
            latencyMetricCard(propertyName = "lastResponse?.maxLatencyInMs", label = "Max")
            latencyMetricCard(propertyName = "lastResponse?.p90LatencyInMs", label = "P90")
            latencyMetricCard(propertyName = "lastResponse?.p95LatencyInMs", label = "P95")
            latencyMetricCard(propertyName = "lastResponse?.p99LatencyInMs", label = "P99")
        }

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
                        div {
                            classes(CARD_ACTIONS, BTN_ACTIONS)
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
                    }
                    div {
                        classes(CARD_BODY)
                        div {
                            id = "monitor-details-latency-chart"
                            style = "min-height: 240px;"
                        }
                    }
                }
            }
        }
    }
}
