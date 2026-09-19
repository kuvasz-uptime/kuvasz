package com.kuvaszuptime.kuvasz.controllers.docker

import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation

interface DockerHostOperations {

    @Operation(summary = "Returns all the configured Docker hosts")
    @Get("/")
    fun getDockerHosts(): List<DockerHostDto>
}
