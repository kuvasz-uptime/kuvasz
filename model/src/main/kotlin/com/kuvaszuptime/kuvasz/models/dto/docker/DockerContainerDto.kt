package com.kuvaszuptime.kuvasz.models.dto.docker

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
@Schema(name = "DockerContainer", description = "A container that exists on a configured Docker host")
data class DockerContainerDto(
    @param:Schema(
        description = "The name of the container, as a Docker monitor would reference it",
        example = "my-app",
    )
    val name: String,
    @param:Schema(description = "The full ID of the container", example = "8dfafdbc3a40")
    val id: String,
    @param:Schema(description = "The image the container runs", example = "nginx:alpine")
    val image: String?,
    @param:Schema(description = "The lifecycle state the daemon reports for the container", example = "running")
    val state: String?,
)
