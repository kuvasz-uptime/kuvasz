package com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderIcmpMonitorsPage(globals: AppGlobals) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.ICMP) { modalId ->
        icmpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
