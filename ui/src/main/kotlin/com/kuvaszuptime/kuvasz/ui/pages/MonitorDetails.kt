package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderHttpMonitorDetailsPage(globals: AppGlobals, monitor: HttpMonitorDetailsDto): String {
    return withLayout(
        globals,
        title = monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH),
        pageTitle = { httpMonitorDetailsHeader(monitor, globals) }
    ) {
        httpMonitorDetailsContent(monitor)
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
        xData("monitorDetails(${monitor.id}, ${monitor.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            httpMonitorDetailsHeading(monitor)

            div {
                classes(COL_MD_AUTO, MS_AUTO, D_PRINT_NONE)
                div {
                    classes(BTN_LIST)
                    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
                        button {
                            classes(BTN, BTN_ICON)
                            xBindDisabled("isRequestLoading")
                            xOnClick("toggleMonitor()")
                            template {
                                attributes["x-if"] = "isMonitorEnabled"
                                icon(Icon.PAUSE)
                            }
                            template {
                                attributes["x-if"] = "!isMonitorEnabled"
                                icon(Icon.PLAY)
                            }
                        }
                    }
                    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
                        buttonWithIcon(Icon.SETTINGS, Messages.configure()) {
                            modalOpener(updateModalId)
                        }
                        compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                            xBindDisabled("isRequestLoading")
                            modalOpener(deleteModalId)
                        }
                        deleteMonitorModal(deleteModalId, monitor.name)
                    } else {
                        buttonWithIcon(Icon.EYE, Messages.configuration()) {
                            modalOpener(updateModalId)
                        }
                    }
                    httpMonitorCreateUpdateModal(updateModalId, monitor, globals)
                }
            }
        }
    }
}
