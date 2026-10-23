package com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderIcmpMonitorsPage(globals: AppGlobals, query: IcmpMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.ICMP, query) { modalId ->
        icmpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
