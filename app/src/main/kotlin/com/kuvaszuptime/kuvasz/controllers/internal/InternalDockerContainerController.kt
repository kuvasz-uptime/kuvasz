package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerContainerDto
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerContainerListingDto
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerListing
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden

@Controller("$API_INTERNAL_PREFIX/docker-hosts")
@Hidden
class InternalDockerContainerController(
    private val dockerHostRegistry: DockerHostRegistry?,
    private val apiClient: DockerApiClient?,
) : InternalDockerContainerOperations {

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getContainers(@PathVariable dockerHostName: String): DockerContainerListingDto {
        // Both beans are gated on there being any docker-hosts configured at all
        val host = dockerHostRegistry?.get(dockerHostName)
        if (host == null || apiClient == null) {
            logger.debug("Cannot list the containers of the unknown Docker host [$dockerHostName]")
            return DockerContainerListingDto(available = false, containers = emptyList())
        }

        return when (val listing = apiClient.listContainers(host, LIST_TIMEOUT_MS)) {
            is DockerContainerListing.Listed -> DockerContainerListingDto(
                available = true,
                containers = listing.containers
                    .map { DockerContainerDto(name = it.name, id = it.id, image = it.image, state = it.state) }
                    .sortedBy { it.name.lowercase() },
            )

            is DockerContainerListing.Unavailable -> {
                logger.info("Could not list the containers of the Docker host [$dockerHostName]: ${listing.reason}")
                DockerContainerListingDto(available = false, containers = emptyList())
            }
        }
    }

    companion object {
        private val logger = loggerFor<InternalDockerContainerController>()

        /**
         * The picker is opened by a person waiting on it, so this is its own budget rather than any monitor's
         * configured timeout: the listing is not tied to a single monitor, and several may name the same host.
         */
        private const val LIST_TIMEOUT_MS = 5_000
    }
}
