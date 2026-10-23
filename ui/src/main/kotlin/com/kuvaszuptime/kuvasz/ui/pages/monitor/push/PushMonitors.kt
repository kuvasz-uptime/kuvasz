package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderPushMonitorsPage(globals: AppGlobals, query: PushMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.PUSH, query) { modalId ->
        pushMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
