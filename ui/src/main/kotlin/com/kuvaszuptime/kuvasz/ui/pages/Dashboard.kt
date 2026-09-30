package com.kuvaszuptime.kuvasz.ui.pages

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
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
    val createHttpModalId = "create-http-monitor-modal"
    val createPushModalId = "create-push-monitor-modal"
    val createIcmpModalId = "create-icmp-monitor-modal"
    val createTcpModalId = "create-tcp-monitor-modal"
    val createDockerModalId = "create-docker-monitor-modal"
    val createDnsModalId = "create-dns-monitor-modal"
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
                            button {
                                val isReadOnly = globals.editabilityState.areHttpMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createHttpModalId)
                                disabled = isReadOnly
                                +Messages.httpSslMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyHttpMonitors())
                                }
                            }
                            button {
                                val isReadOnly = globals.editabilityState.arePushMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createPushModalId)
                                disabled = isReadOnly
                                +Messages.pushMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyPushMonitors())
                                }
                            }
                            button {
                                val isReadOnly = globals.editabilityState.areIcmpMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createIcmpModalId)
                                disabled = isReadOnly
                                +Messages.icmpMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyIcmpMonitors())
                                }
                            }
                            button {
                                val isReadOnly = globals.editabilityState.areTcpMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createTcpModalId)
                                disabled = isReadOnly
                                +Messages.tcpMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyTcpMonitors())
                                }
                            }
                            button {
                                val isReadOnly = globals.editabilityState.areDockerMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createDockerModalId)
                                disabled = isReadOnly
                                +Messages.dockerMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyDockerMonitors())
                                }
                            }
                            button {
                                val isReadOnly = globals.editabilityState.areDnsMonitorsReadOnly()
                                classes(DROPDOWN_ITEM)
                                modalOpener(createDnsModalId)
                                disabled = isReadOnly
                                +Messages.dnsMonitor()
                                if (isReadOnly) {
                                    readOnlyBadge(Messages.readOnlyDnsMonitors())
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
    // Render the upsert modals conditionally
    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
        httpMonitorCreateUpdateModal(modalId = createHttpModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.arePushMonitorsReadOnly()) {
        pushMonitorCreateUpdateModal(modalId = createPushModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.areIcmpMonitorsReadOnly()) {
        icmpMonitorCreateUpdateModal(modalId = createIcmpModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.areTcpMonitorsReadOnly()) {
        tcpMonitorCreateUpdateModal(modalId = createTcpModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.areDnsMonitorsReadOnly()) {
        dnsMonitorCreateUpdateModal(modalId = createDnsModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.areDockerMonitorsReadOnly()) {
        dockerMonitorCreateUpdateModal(modalId = createDockerModalId, monitor = null, globals)
    }
}
