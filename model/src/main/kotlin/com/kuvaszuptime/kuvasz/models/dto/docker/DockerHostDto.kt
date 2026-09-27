package com.kuvaszuptime.kuvasz.models.dto.docker

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
@Schema(name = "DockerHost", description = "A Docker daemon Kuvasz can reach, configured via YAML")
data class DockerHostDto(
    @param:Schema(description = "The unique name of the Docker host, referenced by Docker monitors", example = "vps-1")
    val name: String,
    @param:Schema(description = "The URL of the Docker daemon", example = "tcp://10.0.0.5:2376")
    val url: String,
    @param:Schema(description = "Whether the connection to the daemon is TLS encrypted")
    val tlsEnabled: Boolean,
    @param:Schema(description = "How Kuvasz is authenticated to the daemon")
    val authMethod: DockerHostAuthMethod,
    @param:Schema(
        description = "The Engine API version negotiated with the daemon, null until Kuvasz has talked to it",
        example = "1.44",
    )
    val apiVersion: String?,
)
