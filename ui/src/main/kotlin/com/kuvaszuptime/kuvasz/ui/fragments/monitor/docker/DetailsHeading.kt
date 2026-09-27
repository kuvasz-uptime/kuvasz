package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDockerMonitorDetailsHeading(monitor: DockerMonitorDetailsDto, configuredDockerHosts: List<String>): String =
    buildString { appendHTML().div { dockerMonitorDetailsHeading(monitor, configuredDockerHosts) } }

internal fun FlowContent.dockerMonitorDetailsHeading(
    monitor: DockerMonitorDetailsDto,
    configuredDockerHosts: List<String>,
) =
    monitorDetailsHeading(MonitorTypeUiConfig.DOCKER, monitor) {
        monitorTargetBadge(
            text = "${monitor.dockerHost}/${monitor.container.abbreviate(MONITOR_TARGET_MAX_LENGTH)}",
            icon = Icon.BRAND_DOCKER,
        )
        monitor.image?.let { image ->
            li {
                classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                testId("docker-image-badge")
                inlineStatusBadge(
                    text = image.abbreviate(MONITOR_TARGET_MAX_LENGTH),
                    icon = Icon.BOX,
                    tooltip = "${Messages.dockerImageLabel()}: $image",
                )
            }
        }
        if (monitor.dockerHost !in configuredDockerHosts) {
            li {
                classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                testId("docker-host-not-configured-badge")
                danglingDockerHostBadge(monitor.dockerHost)
            }
        }
    }
