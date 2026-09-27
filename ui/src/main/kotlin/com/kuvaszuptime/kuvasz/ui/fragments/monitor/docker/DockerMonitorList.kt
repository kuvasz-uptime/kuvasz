package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderDockerMonitorList(
    monitors: List<DockerMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
    configuredDockerHosts: List<String>,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        editabilityState = editabilityState,
        columns = listOf(
            uptimeStatusChangedColumn(),
            timestampColumn(Messages.nextCheck(), D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
        nameBadge = { monitor ->
            if (monitor.dockerHost !in configuredDockerHosts) {
                danglingDockerHostIconBadge(monitor.dockerHost)
            }
        },
    )
