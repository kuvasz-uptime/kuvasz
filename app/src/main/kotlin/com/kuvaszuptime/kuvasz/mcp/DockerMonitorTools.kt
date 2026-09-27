package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckDockerMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDto
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class DockerMonitorTools(
    private val dockerMonitorActions: DockerMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_DOCKER_MONITORS,
        description = "Lists all Docker container monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listDockerMonitors(): DockerMonitorListSchema =
        DockerMonitorListSchema(
            monitors = dockerMonitorActions.getMonitorsWithDetails().map { DockerMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_DOCKER_MONITOR_DETAILS,
        description = "Get detailed information about a specific Docker container monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getDockerMonitorDetails(
        @ToolArg(description = "The numeric ID of the Docker monitor") monitorId: Long,
    ): DockerMonitorDetailsSchema =
        DockerMonitorDetailsSchema.fromDto(dockerMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.GET_DOCKER_MONITOR_STATS,
        description = "Get the CPU and memory statistics of the container behind a Docker monitor, plus its uptime " +
            "history. There is no latency here: the only timing a Docker check produces measures the daemon.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getDockerMonitorStats(
        @ToolArg(description = "The numeric ID of the Docker monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): DockerMonitorStatsSchema {
        val stats = dockerMonitorActions.getMonitorStats(monitorId, period.asDuration() ?: DEFAULT_STATS_PERIOD)
        return DockerMonitorStatsSchema.fromDto(stats)
    }

    @Tool(
        name = ToolNames.CREATE_DOCKER_MONITOR,
        description = "Creates a new Docker container monitor. Only 'name', 'dockerHost', 'container' and " +
            "'uptimeCheckInterval' are required; all other fields use sensible defaults. " +
            "'dockerHost' must name a Docker host configured in the YAML config, which cannot be created here. " +
            "Refer to the docs for the default values: https://kuvasz-uptime.dev/management/docker-monitors/." +
            "The available integrations can be found via the $LIST_INTEGRATIONS tool." +
            "This tool will work only if 'areDockerMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckDockerMonitorsWritable
    fun createDockerMonitor(input: DockerMonitorCreatorSchema): DockerMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return DockerMonitorSchema.fromDto(
            DockerMonitorDto.fromMonitorRecord(dockerMonitorActions.createMonitor(creatorDto))
        )
    }

    @Tool(
        name = ToolNames.TOGGLE_DOCKER_MONITOR,
        description = "Enables or disables a Docker container monitor." +
            "This tool will work only if 'areDockerMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckDockerMonitorsWritable
    fun toggleDockerMonitor(
        @ToolArg(description = "The numeric ID of the Docker monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): DockerMonitorSchema =
        DockerMonitorSchema.fromDto(
            DockerMonitorDto.fromMonitorRecord(
                dockerMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
            )
        )

    @Tool(
        name = ToolNames.DELETE_DOCKER_MONITOR,
        description = "Permanently deletes a Docker container monitor by its ID, including all its history " +
            "and events. " +
            "This tool will work only if 'areDockerMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error. " +
            "It will also fail if the monitor is referenced by a read-only status page.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckDockerMonitorsWritable
    fun deleteDockerMonitor(
        @ToolArg(description = "The numeric ID of the Docker monitor") monitorId: Long,
    ): DeleteResultSchema {
        dockerMonitorActions.deleteMonitorById(monitorId)
        return DeleteResultSchema(deleted = true, id = monitorId)
    }
}
