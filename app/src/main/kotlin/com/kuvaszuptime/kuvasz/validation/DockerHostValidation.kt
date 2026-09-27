package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import jakarta.validation.ValidationException

/**
 * The receiver is nullable, because the registry does not exist at all when no `docker-hosts` are configured, which
 * has to count as a missing name too.
 */
fun DockerHostRegistry?.isConfigured(name: String): Boolean = this?.get(name) != null

/**
 * Rejects a Docker host that is not in the config.
 *
 * Only the creation of a monitor is validated this way: the column holds a plain name, so an existing monitor has to
 * stay editable after its host was removed from the config, and its checks report the dangling reference instead.
 */
fun DockerHostRegistry?.requireConfiguredHost(name: String): String {
    if (!isConfigured(name)) throw NonExistingDockerHostException(name)
    return name
}

class NonExistingDockerHostException(name: String) : ValidationException("Non-existing Docker host found: $name.")
