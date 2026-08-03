package com.kuvaszuptime.kuvasz.ui.pages.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderHttpMonitorsPage(globals: AppGlobals) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.HTTP) { modalId ->
        httpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
