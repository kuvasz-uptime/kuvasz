package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

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
            templateTag {
                xIf("lastResponse?.averageLatencyInMs != null")
                div {
                    classes(COL_MD_4)
                    div {
                        classes(CARD)
                        div {
                            classes(CARD_BODY)
                            div {
                                classes(SUBHEADER)
                                +Messages.latencyAverage()
                            }
                            h4 {
                                classes(M_0)
                                xText("lastResponse?.averageLatencyInMs + ' ms'")
                            }
                        }
                    }
                }
            }
            templateTag {
                xIf("lastResponse?.p95LatencyInMs != null")
                div {
                    classes(COL_MD_4)
                    div {
                        classes(CARD)
                        div {
                            classes(CARD_BODY)
                            div {
                                classes(SUBHEADER)
                                +"P95"
                            }
                            h4 {
                                classes(M_0)
                                xText("lastResponse?.p95LatencyInMs + ' ms'")
                            }
                        }
                    }
                }
            }
            templateTag {
                xIf("lastResponse?.p99LatencyInMs != null")
                div {
                    classes(COL_MD_4)
                    div {
                        classes(CARD)
                        div {
                            classes(CARD_BODY)
                            div {
                                classes(SUBHEADER)
                                +"P99"
                            }
                            h4 {
                                classes(M_0)
                                xText("lastResponse?.p99LatencyInMs + ' ms'")
                            }
                        }
                    }
                }
            }
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
