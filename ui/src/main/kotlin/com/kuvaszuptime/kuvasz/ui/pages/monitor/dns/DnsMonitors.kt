package com.kuvaszuptime.kuvasz.ui.pages.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDnsMonitorsPage(globals: AppGlobals, query: DnsMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.DNS, query) { modalId ->
        dnsMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
