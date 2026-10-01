package com.kuvaszuptime.kuvasz.ui.pages

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DASHBOARD_OVERVIEW_ID = "dashboard-overview"

fun renderDashboard(globals: AppGlobals, period: Duration) =
    withLayout(
        globals,
        title = Messages.dashboard(),
        pageTitle = { dashboardHeader(globals, period) }
    ) {
        div {
            id = DASHBOARD_OVERVIEW_ID
            hx {
                get("/fragments/dashboard?period=$period")
                trigger {
                    load()
                    every(30.seconds)
                    event("refresh-dashboard")
                }
                // The verdict in the page header is swapped out-of-band together with the overview
                on(
                    "htmx:before-swap",
                    "if (event.detail.shouldSwap) " +
                        "disposeComponents(this, document.getElementById('$DASHBOARD_STATUS_ID'))",
                )
                on("htmx:after-swap", "reInitTooltips(); initSparklines()")
            }
            htmxLoadingIndicator()
        }
    }

private fun HtmlBlockTag.dashboardHeader(globals: AppGlobals, period: Duration) {
    div {
        classes(CONTAINER_XL)
        div {
            // A bit more room between the rows, when the buttons are wrapped below the status on small screens
            classes(ROW, G_2, GY_3, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_12, COL_MD)
                // Filled in by the overview fragment through an out-of-band swap
                dashboardStatus(stats = null)
            }
            // Next to the status on big screens, in a row of its own below it on small ones
            div {
                classes(COL_12, COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    div {
                        classes(DROPDOWN)
                        a(href = "#") {
                            classes(BTN, DROPDOWN_TOGGLE, BTN_PRIMARY)
                            testId("dashboard-create-monitor-button")
                            dropdownToggler()
                            icon(Icon.PLUS)
                            span {
                                classes(D_NONE, D_MD_BLOCK)
                                +Messages.addNewMonitor()
                            }
                        }
                        div {
                            classes(DROPDOWN_MENU)
                            MonitorTypeUiConfig.entries.forEach { typeUiConfig ->
                                button {
                                    val isReadOnly = globals.editabilityState.areMonitorsReadOnly(typeUiConfig.type)
                                    classes(DROPDOWN_ITEM)
                                    modalOpener(typeUiConfig.createModalId)
                                    disabled = isReadOnly
                                    +typeUiConfig.monitorTitle
                                    if (isReadOnly) {
                                        readOnlyBadge(typeUiConfig.readOnlyNotice)
                                    }
                                }
                            }
                        }
                    }
                    periodSelector(selected = period) {
                        classes(FORM_SELECT, W_AUTO)
                        testId("dashboard-period-selector")
                        ariaLabel(Messages.dashboardPeriod())
                        onChange = "{window.location = '/?period=' + this.value;}"
                    }
                    compactIconButton(Icon.REFRESH, onClick = "refreshDashboard()") {
                        testId("dashboard-refresh-button")
                    }
                }
            }
        }
    }
    // The create modals of the types whose monitors can be edited
    MonitorTypeUiConfig.entries
        .filterNot { globals.editabilityState.areMonitorsReadOnly(it.type) }
        .forEach { createMonitorModal(it, globals) }
}

private fun FlowContent.createMonitorModal(typeUiConfig: MonitorTypeUiConfig, globals: AppGlobals) {
    val modalId = typeUiConfig.createModalId
    when (typeUiConfig) {
        MonitorTypeUiConfig.HTTP -> httpMonitorCreateUpdateModal(modalId, monitor = null, globals)
        MonitorTypeUiConfig.PUSH -> pushMonitorCreateUpdateModal(modalId, monitor = null, globals)
        MonitorTypeUiConfig.ICMP -> icmpMonitorCreateUpdateModal(modalId, monitor = null, globals)
        MonitorTypeUiConfig.TCP -> tcpMonitorCreateUpdateModal(modalId, monitor = null, globals)
        MonitorTypeUiConfig.DNS -> dnsMonitorCreateUpdateModal(modalId, monitor = null, globals)
        MonitorTypeUiConfig.DOCKER -> dockerMonitorCreateUpdateModal(modalId, monitor = null, globals)
    }
}
