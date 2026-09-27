package com.kuvaszuptime.kuvasz.ui.pages.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDockerMonitorsPage(
    globals: AppGlobals,
    categoryFilter: CategoryFilter?,
    availableCategories: List<String>,
) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.DOCKER, categoryFilter, availableCategories) { modalId ->
        dockerMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
