package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderPushMonitorsPage(globals: AppGlobals) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.PUSH) { modalId ->
        pushMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
