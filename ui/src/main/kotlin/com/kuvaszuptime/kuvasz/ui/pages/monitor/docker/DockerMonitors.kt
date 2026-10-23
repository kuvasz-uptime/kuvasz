package com.kuvaszuptime.kuvasz.ui.pages.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDockerMonitorsPage(globals: AppGlobals, query: DockerMonitorListQuery) =
    renderMonitorsPage(globals, MonitorTypeUiConfig.DOCKER, query) { modalId ->
        dockerMonitorCreateUpdateModal(modalId = modalId, monitor = null, globals)
    }
