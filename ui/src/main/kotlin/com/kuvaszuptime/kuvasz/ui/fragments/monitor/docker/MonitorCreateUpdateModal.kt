package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.dockerMonitorCreateUpdateModal(
    modalId: String,
    monitor: DockerMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val hostSelectId = "docker-monitor-host-select"
    val containerSelectId = "docker-monitor-container-select"

    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewDockerMonitor(),
        extraFormArgs = listOf(
            "'$hostSelectId'",
            "'$containerSelectId'",
            globals.configuredDockerHosts.asJsonString(),
            Messages.dockerHostNotConfiguredSuffix().asJsonString(),
        ),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "categoryTooLong" to Messages.errorCategoryTooLong(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "dockerHostRequired" to Messages.errorDockerHostRequired(),
            "dockerHostNotConfigured" to Messages.errorDockerHostNotConfigured(),
            "containerRequired" to Messages.errorDockerContainerRequired(),
            "timeoutMsInvalid" to Messages.errorTimeoutMsInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        ),
    ) { isReadOnlyMode ->
        // Docker host
        div {
            classes(MB_3)
            testId("docker-host-select")
            dockerHostSelector(hostSelectId, isReadOnlyMode, monitor?.dockerHost, globals.configuredDockerHosts)
        }
        // Container
        div {
            classes(MB_3)
            testId("docker-container-select")
            containerSelector(containerSelectId, isReadOnlyMode, monitor?.container)
        }
        // Uptime check interval
        div {
            classes(MB_3)
            validatedInput(
                idPrefix = MonitorTypeUiConfig.DOCKER.slug,
                propName = "uptimeCheckInterval",
                label = Messages.uptimeCheckIntervalLabel(),
                placeholder = null,
                description = null,
                required = true,
                onInput = "validateUptimeCheckInterval()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Timeout (ms)
        div {
            classes(MB_3)
            validatedInput(
                idPrefix = MonitorTypeUiConfig.DOCKER.slug,
                propName = "timeoutMs",
                label = Messages.dockerTimeoutMsLabel(),
                placeholder = null,
                description = Messages.dockerTimeoutMsDescription(),
                required = true,
                onInput = "validateTimeoutMs()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Failure count threshold
        div {
            classes(MB_3)
            validatedInput(
                idPrefix = MonitorTypeUiConfig.DOCKER.slug,
                propName = "failureCountThreshold",
                label = Messages.failureCountThresholdLabel(),
                description = Messages.failureCountThresholdDescription(),
                placeholder = null,
                required = true,
                onInput = "validateFailureCountThreshold()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Metrics History
        div {
            classes(MB_4)
            toggleSwitch(
                propName = "metricsHistoryEnabled",
                label = Messages.metricsHistorySwitchLabel(),
                description = Messages.dockerMetricsHistoryDescription(),
                isDisabled = isReadOnlyMode,
            )
        }
    }
}

/**
 * The Docker host field: a single-value TomSelect over the hosts of the YAML config, which is the only place they
 * can be defined.
 *
 * A monitor may name a host that has since been removed from the config - the column holds a name, not a foreign
 * key - and that value has to survive an edit that does not touch this field. The stored value is therefore
 * rendered as an option of its own, marked as no longer configured, and the form keeps doing the same for the
 * monitors the list page edits through this one shared modal.
 */
private fun FlowContent.dockerHostSelector(
    selectId: String,
    isReadOnlyMode: Boolean,
    currentHost: String?,
    configuredHosts: List<String>,
) {
    formLabel(
        label = Messages.dockerHostLabel(),
        inputName = selectId,
        description = Messages.dockerHostDescription(),
        required = true,
    )
    select {
        classes(FORM_SELECT)
        id = selectId
        name = selectId
        xModel("dockerHost")
        xBindErrorClass("dockerHost")
        // Deferred, because x-model writes the picked value on this very event and may do so after this handler
        xOnChange("\$nextTick(() => onDockerHostChanged())")
        xInitNextTick("{ initDockerHostSelect('#$selectId') }")
        if (isReadOnlyMode) disabled = true
        // What Alpine binds to before anything is picked
        option { value = "" }
        configuredHosts.forEach { host ->
            option {
                value = host
                selected = host == currentHost
                +host
            }
        }
        currentHost?.takeIf { it !in configuredHosts }?.let { danglingHost ->
            option {
                value = danglingHost
                selected = true
                +Messages.dockerHostNotConfiguredOption(danglingHost)
            }
        }
    }
    selectErrorFeedback("dockerHost")
    if (configuredHosts.isEmpty()) {
        div {
            classes(FORM_TEXT, TEXT_DANGER)
            +Messages.dockerHostNoneConfigured()
        }
    }
}

/**
 * The container field: a single-value TomSelect filled from the daemon when the modal opens, and refilled whenever
 * the host changes.
 *
 * It accepts a typed-in value too, because the listing is best effort: the daemon may be unreachable, a socket
 * proxy may not allow the endpoint, and a container may simply not exist yet. The stored value is rendered as an
 * option up front for the same reason the host's is.
 */
private fun FlowContent.containerSelector(
    selectId: String,
    isReadOnlyMode: Boolean,
    currentContainer: String?,
) {
    formLabel(
        label = Messages.dockerContainerLabel(),
        inputName = selectId,
        description = Messages.dockerContainerDescription(),
        required = true,
    )
    select {
        classes(FORM_SELECT)
        id = selectId
        name = selectId
        xModel("container")
        xBindErrorClass("container")
        xOnChange("\$nextTick(() => validateContainer())")
        xInitNextTick(
            "{ initDockerContainerSelect('#$selectId', ${Messages.dockerContainerLoading().asJsonString()}, " +
                "${Messages.dockerContainerNoResults().asJsonString()}); " +
                "watchModal() }"
        )
        if (isReadOnlyMode) disabled = true
        option { value = "" }
        currentContainer?.let { container ->
            option {
                value = container
                selected = true
                +container
            }
        }
    }
    selectErrorFeedback("container")
    templateTag {
        xIf("containerLoadFailed")
        div {
            classes(FORM_TEXT, TEXT_WARNING)
            +Messages.dockerContainerLoadFailed()
        }
    }
}

/**
 * TomSelect nests the `select` into a wrapper of its own, so the sibling selector Bootstrap uses for the
 * validation feedback cannot reach it.
 */
private fun FlowContent.selectErrorFeedback(propName: String) {
    templateTag {
        xIf("errors.$propName")
        div {
            classes(INVALID_FEEDBACK, D_BLOCK)
            xText("errors.$propName")
        }
    }
}
