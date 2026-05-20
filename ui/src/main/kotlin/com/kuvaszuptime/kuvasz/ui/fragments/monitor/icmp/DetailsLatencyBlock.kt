package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
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

private fun FlowContent.packetLossMetricCard(propertyName: String, label: String) {
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
                        xText("$propertyName + '%'")
                    }
                }
            }
        }
    }
}

internal fun FlowContent.icmpDetailsMetricsBlock(monitor: IcmpMonitorDetailsDto) {
    div {
        xData(
            """icmpMetricsBlock(
            |${monitor.id},
            |${monitor.enabled},
            |${monitor.uptimeCheckInterval},
            |"${Messages.latencyChartNoData()}",
            |${UIDefaults.ICMP_MONITOR_METRICS_PERIOD_HOURS}
            |)
            """.trimMargin()
        )
        xOn("monitor-disabled.window", "isAutoRefreshEnabled = false")

        // Metrics heading with global auto-refresh toggle
        div {
            classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_3)
            h2 {
                classes(MB_0)
                +Messages.metrics()
                span {
                    classes(BADGE)
                    +Messages.lastXHours(UIDefaults.ICMP_MONITOR_METRICS_PERIOD_HOURS)
                }
            }
            div {
                classes(MS_AUTO)
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

        // Latency section
        h3 { +Messages.latencyBlockTitle() }
        div {
            classes(ROW, ROW_CARDS, MB_3)
            latencyMetricCard(
                propertyName = "lastResponse?.latencyStats?.averageLatencyInMs",
                label = Messages.latencyAverage(),
            )
            latencyMetricCard(propertyName = "lastResponse?.latencyStats?.minLatencyInMs", label = "Min")
            latencyMetricCard(propertyName = "lastResponse?.latencyStats?.maxLatencyInMs", label = "Max")
            latencyMetricCard(propertyName = "lastResponse?.latencyStats?.p90LatencyInMs", label = "P90")
            latencyMetricCard(propertyName = "lastResponse?.latencyStats?.p95LatencyInMs", label = "P95")
            latencyMetricCard(propertyName = "lastResponse?.latencyStats?.p99LatencyInMs", label = "P99")
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
                    }
                    div {
                        classes(CARD_BODY)
                        div {
                            id = "icmp-monitor-details-latency-chart"
                            style = "min-height: 240px;"
                        }
                    }
                }
            }
        }

        // Packet loss section
        h3 { +Messages.packetLossBlockTitle() }
        div {
            classes(ROW, ROW_CARDS, MB_3)
            packetLossMetricCard(
                propertyName = "lastResponse?.packetLossStats?.averagePacketLossPercentage",
                label = Messages.latencyAverage(),
            )
            packetLossMetricCard(propertyName = "lastResponse?.packetLossStats?.minPacketLossPercentage", label = "Min")
            packetLossMetricCard(propertyName = "lastResponse?.packetLossStats?.maxPacketLossPercentage", label = "Max")
            packetLossMetricCard(propertyName = "lastResponse?.packetLossStats?.p90PacketLossPercentage", label = "P90")
            packetLossMetricCard(propertyName = "lastResponse?.packetLossStats?.p95PacketLossPercentage", label = "P95")
            packetLossMetricCard(propertyName = "lastResponse?.packetLossStats?.p99PacketLossPercentage", label = "P99")
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
                    }
                    div {
                        classes(CARD_BODY)
                        div {
                            id = "icmp-monitor-details-packet-loss-chart"
                            style = "min-height: 240px;"
                        }
                    }
                }
            }
        }
    }
}
