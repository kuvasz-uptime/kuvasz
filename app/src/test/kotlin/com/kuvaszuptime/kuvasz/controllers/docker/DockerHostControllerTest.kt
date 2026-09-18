package com.kuvaszuptime.kuvasz.controllers.docker

import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [DOCKER_HOSTS])
class DockerHostControllerTest(private val dockerHostClient: DockerHostClient) : ShouldSpec({

    context("the getDockerHosts endpoint") {

        should("return every configured Docker host, sorted by name") {
            dockerHostClient.getDockerHosts() shouldContainExactly listOf(
                DockerHostDto(name = "local", url = "unix:///var/run/docker.sock", tlsEnabled = false),
                DockerHostDto(name = "vps-1", url = "tcp://10.0.0.5:2375", tlsEnabled = false),
            )
        }
    }
})
