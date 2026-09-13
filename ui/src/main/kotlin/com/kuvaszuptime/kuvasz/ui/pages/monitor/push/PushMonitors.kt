package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderPushMonitorsPage(
    globals: AppGlobals,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.PUSH, categoryFilter, availableCategories) { modalId ->
        pushMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
