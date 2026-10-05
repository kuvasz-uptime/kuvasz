package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.DockerHostListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerHostSchema
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import io.micronaut.mcp.annotations.Tool
import jakarta.inject.Singleton

@Singleton
class DockerHostTools(
    // Both absent when no Docker hosts are configured at all
    private val dockerHostRegistry: DockerHostRegistry?,
    private val dockerApiClient: DockerApiClient?,
) {

    @Tool(
        name = ToolNames.LIST_DOCKER_HOSTS,
        description = "Lists the Docker hosts configured in the YAML config, which Docker monitors reference by " +
            "their name, with their URL, the way Kuvasz authenticates to them, and the Engine API version " +
            "negotiated with them so far. Docker hosts can't be created through this server.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listDockerHosts(): DockerHostListSchema = DockerHostListSchema(
        dockerHostRegistry?.getHostDtos(dockerApiClient).orEmpty().map { DockerHostSchema.fromDto(it) }
    )
}
