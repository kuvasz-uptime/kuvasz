package com.kuvaszuptime.kuvasz.ui.pages.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDnsMonitorsPage(
    globals: AppGlobals,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.DNS, categoryFilter, availableCategories) { modalId ->
        dnsMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
