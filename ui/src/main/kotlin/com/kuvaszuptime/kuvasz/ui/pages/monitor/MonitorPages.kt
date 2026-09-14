package com.kuvaszuptime.kuvasz.ui.pages.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
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
 * the header with the create modal of the type, the category filter, and the placeholder the list is swapped into.
 *
 * The filter is rendered here rather than inside the swapped fragment on purpose: the list refreshes itself every
 * few seconds, and a filter living inside it would be torn out from under the cursor. Its current value travels in
 * the `category` query parameter, so a filtered list can be linked to, and it is baked into the htmx URL below, so
 * every refresh keeps it.
 */
internal fun renderMonitorsPage(
    globals: AppGlobals,
    typeUiConfig: MonitorTypeUiConfig,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
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
                categoryFilterToolbar(typeUiConfig, categoryFilter, availableCategories)
                div {
                    hx {
                        get(typeUiConfig.listFragmentPath(categoryFilter))
                        trigger {
                            load()
                            event("refresh-monitor-list")
                            every(15.seconds)
                        }
                        onSwapReinitTooltips()
                    }
                    id = typeUiConfig.listElementId
                    htmxLoadingIndicator()
                }
            }
        }
    }
}

private fun FlowContent.categoryFilterToolbar(
    typeUiConfig: MonitorTypeUiConfig,
    selected: CategoryFilter?,
    availableCategories: List<String>,
) {
    div {
        classes(CARD_HEADER, PY_2)
        testId("category-filter")
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER, W_100)
            div {
                classes(COL_12, COL_SM_AUTO, MS_SM_AUTO)
                div {
                    classes(INPUT_ICON)
                    span {
                        classes(INPUT_ICON_ADDON)
                        icon(Icon.TAG)
                    }
                    select {
                        classes(FORM_SELECT, FORM_SELECT_SM)
                        ariaLabel(Messages.filterByCategory())
                        onChange = "{window.location = '${typeUiConfig.listPath}' + this.value;}"
                        // An absent parameter means every monitor, so the unfiltered page keeps its plain URL
                        option {
                            value = ""
                            this.selected = selected == null
                            +Messages.allCategories()
                        }
                        availableCategories.forEach { category ->
                            option {
                                value = "?category=${category.urlEncode()}"
                                this.selected = selected == CategoryFilter.InCategory(category)
                                +category
                            }
                        }
                        // ...and an empty one the monitors that have no category at all
                        option {
                            value = "?category="
                            this.selected = selected == CategoryFilter.Uncategorized
                            +Messages.uncategorizedMonitors()
                        }
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
        upsertModal(createModalId)
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
