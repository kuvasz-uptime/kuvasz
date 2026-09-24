package com.kuvaszuptime.kuvasz.services.docker

data class DockerContainer(
    val name: String,
    val id: String,
    val image: String?,
    val state: String?,
)

sealed interface DockerContainerListing {

    data class Listed(val containers: List<DockerContainer>) : DockerContainerListing

    data class Unavailable(val reason: String) : DockerContainerListing
}
