package com.kuvaszuptime.kuvasz.controllers.docker

import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostAuthMethod
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk

@MicronautTest(environments = [DOCKER_HOSTS])
class DockerHostControllerTest(private val dockerHostClient: DockerHostClient) : ShouldSpec({

    context("the getDockerHosts endpoint") {

        should("return every configured Docker host, sorted by name, with the API version negotiated so far") {
            dockerHostClient.getDockerHosts() shouldContainExactly listOf(
                DockerHostDto(
                    name = "local",
                    url = "unix:///var/run/docker.sock",
                    tlsEnabled = false,
                    authMethod = DockerHostAuthMethod.UNIX_SOCKET,
                    apiVersion = null,
                ),
                DockerHostDto(
                    name = "vps-1",
                    url = "tcp://10.0.0.5:2375",
                    tlsEnabled = false,
                    authMethod = DockerHostAuthMethod.NONE,
                    apiVersion = "1.44",
                ),
            )
        }
    }
}) {

    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk {
        every { negotiatedVersions() } returns mapOf("vps-1" to "1.44")
    }
}
