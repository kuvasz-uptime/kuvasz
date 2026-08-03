package com.kuvaszuptime.kuvasz.ui.pages.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

/**
 * The page listing every monitor of a type. The list itself is loaded and refreshed by htmx, so all this renders is
 * the header with the create modal of the type and the placeholder the list is swapped into.
 */
internal fun renderMonitorsPage(
    globals: AppGlobals,
    typeUiConfig: MonitorTypeUiConfig,
    upsertModal: FlowContent.(modalId: String) -> Unit,
) = withLayout(
    globals,
    title = typeUiConfig.listPageTitle,
    pageTitle = { monitorsHeader(globals, typeUiConfig, upsertModal) }
) {
    div {
        classes(ROW, ROW_CARDS)
        div {
            classes(COL_12)
            div {
                classes(CARD)
                div {
                    hx {
                        get(typeUiConfig.fragmentPath("list"))
                        trigger {
                            load()
                            event("refresh-monitor-list")
                            every(15.seconds)
                        }
                        onSwapReinitTooltips()
                    }
                    id = typeUiConfig.listElementId
                    div {
                        classes(SPINNER_GROW, HTMX_INDICATOR)
                        role = "status"
                    }
                }
            }
        }
    }
}

private fun HtmlBlockTag.monitorsHeader(
    globals: AppGlobals,
    typeUiConfig: MonitorTypeUiConfig,
    upsertModal: FlowContent.(modalId: String) -> Unit,
) {
    val createModalId = typeUiConfig.createModalId
    val isReadOnlyMode = globals.editabilityState.areMonitorsReadOnly(typeUiConfig.type)
    div {
        classes(CONTAINER_XL)
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER)
            div {
                classes(CSSClass.COL)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER)
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(PAGE_PRETITLE)
                            +Messages.monitors()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +typeUiConfig.title
                            // Read only notice
                            if (isReadOnlyMode) {
                                readOnlyBadge(typeUiConfig.readOnlyNotice)
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!isReadOnlyMode) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createModalId)
                                    testId("add-new-button")
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = typeUiConfig.refreshListCall) {}
                        }
                    }
                }
            }
        }
        if (!isReadOnlyMode) {
            upsertModal(createModalId)
        }
    }
}

/**
 * The details page of a single monitor. [heading] and [content] are what the type shows about itself, everything
 * around them -- the toggle/configure/delete actions and the upsert modal -- is the same for every type.
 */
internal fun renderMonitorDetailsPage(
    globals: AppGlobals,
    monitor: MonitorDetailsDto,
    typeUiConfig: MonitorTypeUiConfig,
    heading: FlowContent.() -> Unit,
    upsertModal: FlowContent.(modalId: String) -> Unit,
    content: HtmlBlockTag.() -> Unit,
): String = withLayout(
    globals,
    title = monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH),
    pageTitle = { monitorDetailsHeader(globals, monitor, typeUiConfig, heading, upsertModal) },
    content = content,
)

private fun HtmlBlockTag.monitorDetailsHeader(
    globals: AppGlobals,
    monitor: MonitorDetailsDto,
    typeUiConfig: MonitorTypeUiConfig,
    heading: FlowContent.() -> Unit,
    upsertModal: FlowContent.(modalId: String) -> Unit,
) {
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    val updateModalId = "update-monitor-modal-${monitor.id}"
    val isReadOnlyMode = globals.editabilityState.areMonitorsReadOnly(typeUiConfig.type)

    div {
        classes(CONTAINER)
        xData("${typeUiConfig.alpineComponent("MonitorDetails")}(${monitor.id}, ${monitor.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            heading()

            div {
                classes(COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    if (!isReadOnlyMode) {
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
                    upsertModal(updateModalId)
                }
            }
        }
    }
}
