package com.kuvaszuptime.kuvasz.ui.pages.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderTcpMonitorsPage(globals: AppGlobals, query: TcpMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.TCP, query) { modalId ->
        tcpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
