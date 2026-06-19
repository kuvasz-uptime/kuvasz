package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderHttpMonitorList(
    monitors: List<HttpMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = editabilityState.areHttpMonitorsReadOnly()
        if (monitors.isNotEmpty()) {
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                table {
                    classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                    thead {
                        tr {
                            th { +Messages.name() }
                            th {
                                classes(TEXT_CENTER)
                                +Messages.status()
                            }
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                +"SSL"
                            }
                            th {
                                classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                +Messages.changed()
                            }
                            th {
                                classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                +Messages.nextUptimeCheck()
                            }
                            if (!isReadOnlyMode) {
                                // Actions
                                th {}
                            }
                        }
                    }
                    tbody {
                        monitors.forEach { monitor ->
                            tr {
                                testId("http-monitor-row")
                                xData(
                                    """httpMonitorListItem(
                                    |${monitor.id}, 
                                    |${monitor.enabled}, 
                                    |${monitor.statusPages.isNotEmpty()}
                                    |)
                                    """.trimMargin()
                                )
                                td {
                                    a(href = "/http-monitors/${monitor.id}") {
                                        classes(TEXT_RESET)
                                        span {
                                            classes(TEXT_WRAP, TEXT_BREAK)
                                            tooltip(title = monitor.url.toString(), location = TooltipLocation.RIGHT)
                                            +monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH)
                                        }
                                    }
                                }
                                td {
                                    classes(TEXT_CENTER)
                                    uptimeBadgeOfMonitor(monitor, withTooltip = true)
                                }
                                td {
                                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                    sslStatusOfMonitor(monitor, withTooltip = true)
                                }
                                td {
                                    classes(
                                        TEXT_NOWRAP,
                                        D_NONE,
                                        D_LG_TABLE_CELL,
                                        TEXT_CENTER
                                    )
                                    span {
                                        monitor.uptimeStatusStartedAt?.let { startedAt ->
                                            tooltip(title = startedAt.toDateTimeString())
                                            +startedAt.timeAgo()
                                        }
                                    }
                                }
                                td {
                                    classes(
                                        TEXT_NOWRAP,
                                        D_NONE,
                                        D_LG_TABLE_CELL,
                                        TEXT_CENTER
                                    )
                                    span {
                                        monitor.nextUptimeCheck?.let { nextCheck ->
                                            tooltip(title = nextCheck.toDateTimeString())
                                            +nextCheck.timeAgo()
                                        }
                                    }
                                }
                                if (!isReadOnlyMode) {
                                    val deleteModalId = "delete-monitor-modal-${monitor.id}"
                                    td {
                                        classes(TEXT_CENTER)
                                        div {
                                            classes(FLEX_NOWRAP, BTN_GROUP)
                                            val toggleIcon = if (monitor.enabled) Icon.PAUSE else Icon.PLAY
                                            compactIconButton(toggleIcon) {
                                                testId("http-monitor-toggle-button")
                                                xBindDisabled("isRequestLoading")
                                                xOnClick("toggleMonitor()")
                                            }
                                            compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                                                testId("http-monitor-delete-button")
                                                xBindDisabled("isRequestLoading")
                                                modalOpener(deleteModalId)
                                            }
                                        }
                                        // Delete modal
                                        val isDeleteDisabled = monitor.statusPages.isNotEmpty() &&
                                            editabilityState.areStatusPagesReadOnly()
                                        deleteMonitorModal(
                                            modalId = deleteModalId,
                                            monitorName = monitor.name,
                                            isDeleteDisabled = isDeleteDisabled,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            div {
                classes(CARD_BODY)
                p {
                    classes(TEXT_SECONDARY, TEXT_CENTER)
                    +Messages.noMonitors()
                }
            }
        }
    }
