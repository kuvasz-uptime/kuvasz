package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderPushMonitorDetailsPage(
    globals: AppGlobals,
    monitor: PushMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String {
    return withLayout(
        globals,
        title = monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH),
        pageTitle = { pushMonitorDetailsHeader(monitor, globals) }
    ) {
        pushMonitorDetailsContent(monitor, stats)
    }
}

internal fun HtmlBlockTag.pushMonitorDetailsHeader(
    monitor: PushMonitorDetailsDto,
    globals: AppGlobals,
) {
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    val updateModalId = "update-monitor-modal-${monitor.id}"

    div {
        classes(CONTAINER)
        xData("pushMonitorDetails(${monitor.id}, ${monitor.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            pushMonitorDetailsHeading(monitor)

            div {
                classes(COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    if (!globals.editabilityState.arePushMonitorsReadOnly()) {
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
                    if (!globals.editabilityState.arePushMonitorsReadOnly()) {
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
                    pushMonitorCreateUpdateModal(updateModalId, monitor, globals)
                }
            }
        }
    }
}
