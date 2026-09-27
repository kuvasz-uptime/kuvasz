package com.kuvaszuptime.kuvasz.controllers.docker

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * `DockerHostRegistry` is gated on the presence of `docker-hosts` config beans, so it is absent here. The endpoint
 * itself stays available and reports an empty list, instead of disappearing from the API.
 */
@MicronautTest
class DockerHostControllerWithoutHostsTest(private val dockerHostClient: DockerHostClient) : ShouldSpec({

    context("the getDockerHosts endpoint without any configured Docker host") {

        should("return an empty list rather than fail") {
            dockerHostClient.getDockerHosts().shouldBeEmpty()
        }
    }
})
