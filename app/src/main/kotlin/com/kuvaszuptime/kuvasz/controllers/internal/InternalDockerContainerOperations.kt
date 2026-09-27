package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.models.dto.docker.DockerContainerListingDto
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable

interface InternalDockerContainerOperations {

    /**
     * The containers of a configured host, for the container picker of the Docker monitor form. It answers 200 even
     * when the host is unknown or its daemon cannot be reached, reporting that through `available` instead, so that
     * the form degrades to a free-text entry rather than blocking the edit.
     */
    @Get("/{dockerHostName}/containers")
    fun getContainers(@PathVariable dockerHostName: String): DockerContainerListingDto
}
