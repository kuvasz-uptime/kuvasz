package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderMonitorDetailsPage(globals: AppGlobals, monitor: MonitorDetailsDto): String {
    return withLayout(
        globals,
        title = monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH),
        pageTitle = { monitorDetailsHeader(monitor, globals) }
    ) {
        monitorDetailsContent(monitor)
    }
}

internal fun HtmlBlockTag.monitorDetailsHeader(
    monitor: MonitorDetailsDto,
    globals: AppGlobals,
) {
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    val updateModalId = "update-monitor-modal-${monitor.id}"

    div {
        classes(CONTAINER)
        xData("monitorDetails(${monitor.id}, ${monitor.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            monitorDetailsHeading(monitor)

            div {
                classes(COL_MD_AUTO, MS_AUTO, D_PRINT_NONE)
                div {
                    classes(BTN_LIST)
                    if (!globals.isReadOnlyMode()) {
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
                    if (!globals.isReadOnlyMode()) {
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
                    monitorCreateUpdateModal(updateModalId, monitor, globals)
                }
            }
        }
    }
}
