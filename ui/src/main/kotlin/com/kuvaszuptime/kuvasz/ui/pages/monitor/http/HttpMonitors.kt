package com.kuvaszuptime.kuvasz.ui.pages.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderHttpMonitorsPage(globals: AppGlobals, query: HttpMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.HTTP, query) { modalId ->
        httpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
