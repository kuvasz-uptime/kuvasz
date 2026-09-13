package com.kuvaszuptime.kuvasz.ui.pages.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderTcpMonitorsPage(
    globals: AppGlobals,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.TCP, categoryFilter, availableCategories) { modalId ->
        tcpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
