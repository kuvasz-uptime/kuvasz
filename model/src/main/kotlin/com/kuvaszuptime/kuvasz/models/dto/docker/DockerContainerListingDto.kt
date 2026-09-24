package com.kuvaszuptime.kuvasz.models.dto.docker

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

/**
 * The containers of a Docker host, together with whether the daemon could be listed at all.
 *
 * The two are reported separately on purpose: a host that genuinely runs nothing and a daemon that could not be
 * reached both produce no containers, but only the second one should tell the operator that the picker is empty
 * for a reason and that a name can still be typed in.
 */
@Introspected
@Schema(name = "DockerContainerListing", description = "The containers a configured Docker host reports")
data class DockerContainerListingDto(
    @param:Schema(description = "Whether the daemon could be listed at all")
    val available: Boolean,
    @param:Schema(description = "The containers of the host, empty when the listing is unavailable")
    val containers: List<DockerContainerDto>,
)
