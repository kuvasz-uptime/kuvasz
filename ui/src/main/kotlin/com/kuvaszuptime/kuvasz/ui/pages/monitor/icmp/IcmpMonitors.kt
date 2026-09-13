package com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderIcmpMonitorsPage(
    globals: AppGlobals,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.ICMP, categoryFilter, availableCategories) { modalId ->
        icmpMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
