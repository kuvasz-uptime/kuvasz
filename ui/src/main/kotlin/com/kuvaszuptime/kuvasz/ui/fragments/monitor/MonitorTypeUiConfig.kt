package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.icons.*

/**
 * Everything the shared monitor fragments need to know about a concrete monitor type: how it is branded on the UI and
 * how its routes, DOM ids and Alpine.js components are named. All of the latter are derived from
 * [MonitorType.identifier], which is also what the backend routes and the JS components are built from.
 */
internal enum class MonitorTypeUiConfig(
    val type: MonitorType,
    val title: String,
    val icon: Icon,
    val color: Color,
    // The dashboard uses a shorter label than the pages, where there is enough room for the full one
    val dashboardTitle: String = title,
) {
    HTTP(MonitorType.HTTP_SSL, title = "HTTP & SSL", Icon.WORLD, Color.BLUE_LT, dashboardTitle = "HTTP"),
    PUSH(MonitorType.PUSH, title = "Push", Icon.HEARTBEAT, Color.RED_LT),
    ICMP(MonitorType.ICMP, title = "ICMP", Icon.WAVE_SQUARE, Color.ORANGE_LT),
    TCP(MonitorType.TCP, title = "TCP", Icon.NETWORK, Color.PURPLE_LT),
    DNS(MonitorType.DNS, title = "DNS", Icon.CLOUD_QUESTION, Color.CYAN_LT);

    /** The identifier as it appears in routes, DOM ids and lower-camel-case JS names, e.g. `dns`. */
    val slug: String get() = type.identifier

    /** The identifier as it appears inside upper-camel-case JS names, e.g. `Dns` in `refreshDnsMonitorList`. */
    private val slugCapitalized: String get() = slug.replaceFirstChar { it.uppercase() }

    val listPath: String get() = "/$slug-monitors"

    val listElementId: String get() = "$slug-monitors-list"

    val createModalId: String get() = "create-$slug-monitor-modal"

    val refreshListCall: String get() = "refresh${slugCapitalized}MonitorList()"

    val upsertFormComponent: String get() = "upsert${slugCapitalized}MonitorForm"

    /** The name of the Alpine.js component registered for this type, e.g. `dnsMonitorDetails`. */
    fun alpineComponent(name: String): String = "$slug$name"

    val listPageTitle: String
        get() = when (this) {
            HTTP -> Messages.httpSslMonitors()
            PUSH -> Messages.pushMonitors()
            ICMP -> Messages.icmpMonitors()
            TCP -> Messages.tcpMonitors()
            DNS -> Messages.dnsMonitors()
        }

    val readOnlyNotice: String
        get() = when (this) {
            HTTP -> Messages.readOnlyHttpMonitors()
            PUSH -> Messages.readOnlyPushMonitors()
            ICMP -> Messages.readOnlyIcmpMonitors()
            TCP -> Messages.readOnlyTcpMonitors()
            DNS -> Messages.readOnlyDnsMonitors()
        }

    fun testId(suffix: String): String = "$slug-monitor-$suffix"

    fun detailsPath(monitorId: Long): String = "$listPath/$monitorId"

    fun fragmentPath(fragment: String): String = "$listPath/fragments/$fragment"

    companion object {
        fun of(type: MonitorType): MonitorTypeUiConfig = entries.first { it.type == type }

        fun of(monitor: MonitorDetailsDto): MonitorTypeUiConfig = of(monitor.monitorType())
    }
}
