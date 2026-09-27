package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.services.docker.DockerContainer
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerListing
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private fun container(name: String, state: String = "running") =
    DockerContainer(name = name, id = "id-$name", image = "nginx:alpine", state = state)

@MicronautTest(environments = [DOCKER_HOSTS])
class InternalDockerContainerControllerTest(
    private val client: InternalDockerContainerClient,
    private val apiClient: DockerApiClient,
) : DatabaseBehaviorSpec() {

    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk()

    init {
        given("the containers endpoint of a Docker host") {

            `when`("the host is configured and the daemon answers") {
                val mock = getMock(apiClient)
                every { mock.listContainers(any(), any()) } returns DockerContainerListing.Listed(
                    listOf(container("zeta"), container("Alpha", state = "exited")),
                )

                val listing = client.getContainers("local")

                then("it should return them sorted by name, case-insensitively") {
                    listing.available shouldBe true
                    listing.containers.map { it.name } shouldBe listOf("Alpha", "zeta")
                }

                then("it should carry what the picker shows beside the name") {
                    val first = listing.containers.first()
                    first.id shouldBe "id-Alpha"
                    first.image shouldBe "nginx:alpine"
                    first.state shouldBe "exited"
                }
            }

            // Degrading to a free-text entry beats blocking the edit of a monitor
            `when`("the daemon cannot be reached") {
                val mock = getMock(apiClient)
                every {
                    mock.listContainers(any(), any())
                } returns DockerContainerListing.Unavailable("connection refused")

                val listing = client.getContainers("local")

                then("it should answer with an empty list rather than an error") {
                    listing.containers.shouldBeEmpty()
                }

                // What tells the form to offer a typed-in name instead of silently showing an empty picker
                then("it should report the listing as unavailable") {
                    listing.available shouldBe false
                }
            }

            `when`("the host is not configured") {
                then("it should answer with an unavailable, empty listing") {
                    val listing = client.getContainers("no-such-host")

                    listing.available shouldBe false
                    listing.containers.shouldBeEmpty()
                }

                then("the daemon should not have been called") {
                    client.getContainers("no-such-host")

                    verify(exactly = 0) { getMock(apiClient).listContainers(any(), any()) }
                }
            }

            `when`("the daemon reports no container") {
                val mock = getMock(apiClient)
                every { mock.listContainers(any(), any()) } returns DockerContainerListing.Listed(emptyList())

                val listing = client.getContainers("vps-1")

                // Available, unlike the failure cases: this host genuinely runs nothing
                then("it should answer with an available, empty listing") {
                    listing.available shouldBe true
                    listing.containers.shouldBeEmpty()
                }
            }
        }
    }
}
