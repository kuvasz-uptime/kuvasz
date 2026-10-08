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
            icon = Icon.BOX,
        )
        monitor.image?.let { image ->
            li {
                classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                testId("docker-image-badge")
                inlineStatusBadge(
                    text = image.abbreviate(MONITOR_TARGET_MAX_LENGTH),
                    icon = Icon.CUBE_3D_SPHERE,
                    tooltip = monitor.containerTooltip(image),
                    tooltipHtml = true,
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

private fun DockerMonitorDetailsDto.containerTooltip(image: String): String =
    createHTML(prettyPrint = false).span {
        +"${Messages.dockerImageLabel()}: "
        strong { +image }
        restartCount?.let { count ->
            br()
            +"${Messages.dockerRestartCountLabel()}: "
            strong { +count.toString() }
        }
        containerCreatedAt?.let { createdAt ->
            br()
            +"${Messages.dockerContainerCreatedAtLabel()}: "
            // The tooltip is narrow, so the timestamp would otherwise break between its date and time
            strong {
                classes(TEXT_NOWRAP)
                +createdAt.toDateTimeString()
            }
        }
    }
