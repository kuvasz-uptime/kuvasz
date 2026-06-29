package com.kuvaszuptime.kuvasz.ui.pages.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderHttpMonitorDetailsPage(
    globals: AppGlobals,
    monitor: HttpMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String {
    return withLayout(
        globals,
        title = monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH),
        pageTitle = { httpMonitorDetailsHeader(monitor, globals) }
    ) {
        httpMonitorDetailsContent(monitor, stats)
    }
}

internal fun HtmlBlockTag.httpMonitorDetailsHeader(
    monitor: HttpMonitorDetailsDto,
    globals: AppGlobals,
) {
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    val updateModalId = "update-monitor-modal-${monitor.id}"

    div {
        classes(CONTAINER)
        xData("httpMonitorDetails(${monitor.id}, ${monitor.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            httpMonitorDetailsHeading(monitor)

            div {
                classes(COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
                        button {
                            classes(BTN, BTN_ICON)
                            testId("toggle-monitor-button")
                            xBindDisabled("isRequestLoading")
                            xOnClick("toggleMonitor()")
                            template {
                                xIf("isMonitorEnabled")
                                icon(Icon.PAUSE)
                            }
                            template {
                                xIf("!isMonitorEnabled")
                                icon(Icon.PLAY)
                            }
                        }
                    }
                    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
                        buttonWithIcon(Icon.SETTINGS, Messages.configure()) {
                            modalOpener(updateModalId)
                            testId("configure-button")
                        }
                        compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                            xBindDisabled("isRequestLoading")
                            modalOpener(deleteModalId)
                        }
                        val isDeleteDisabled = monitor.statusPages.isNotEmpty() &&
                            globals.editabilityState.areStatusPagesReadOnly()
                        deleteMonitorModal(deleteModalId, monitor.name, isDeleteDisabled)
                    } else {
                        buttonWithIcon(Icon.EYE, Messages.configuration()) {
                            modalOpener(updateModalId)
                            testId("configuration-button")
                        }
                    }
                    httpMonitorCreateUpdateModal(updateModalId, monitor, globals)
                }
            }
        }
    }
}
