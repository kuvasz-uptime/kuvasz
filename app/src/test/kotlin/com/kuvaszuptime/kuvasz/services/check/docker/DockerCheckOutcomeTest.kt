package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.services.docker.DockerContainerState
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.OffsetDateTime

private const val DOCKER_HOST = "vps-1"
private const val CONTAINER = "my-app"
private const val LATENCY_MS = 42

private fun inspected(
    status: DockerContainerStatus,
    health: DockerHealthStatus = DockerHealthStatus.NONE,
    exitCode: Int? = null,
    oomKilled: Boolean = false,
    failingStreak: Int? = null,
    image: String? = null,
    restartCount: Int? = null,
    createdAt: OffsetDateTime? = null,
) = DockerInspectResult.Inspected(
    state = DockerContainerState(
        status = status,
        health = health,
        exitCode = exitCode,
        oomKilled = oomKilled,
        failingStreak = failingStreak,
        image = image,
        restartCount = restartCount,
        createdAt = createdAt,
    ),
    latencyMs = LATENCY_MS,
)

private fun DockerInspectResult.outcome() = toCheckOutcome(DOCKER_HOST, CONTAINER)

private fun DockerInspectResult.downError(): String =
    outcome().shouldBeInstanceOf<DockerCheckOutcome.Down>().error

class DockerCheckOutcomeTest : BehaviorSpec({

    given("a running container") {

        DockerHealthStatus.entries.filterNot { it == DockerHealthStatus.UNHEALTHY }.forEach { health ->
            `when`("its health is $health") {
                val outcome = inspected(DockerContainerStatus.RUNNING, health = health).outcome()

                then("it should be UP with the API latency") {
                    outcome shouldBe DockerCheckOutcome.Up(
                        latencyMs = LATENCY_MS,
                        image = null,
                        restartCount = null,
                        containerCreatedAt = null,
                    )
                }
            }
        }

        `when`("its healthcheck reports it unhealthy without a failing streak") {
            val outcome = inspected(DockerContainerStatus.RUNNING, health = DockerHealthStatus.UNHEALTHY).outcome()

            then("it should be DOWN, still carrying the API latency") {
                outcome shouldBe DockerCheckOutcome.Down(
                    error = "The container is running, but its healthcheck reports it unhealthy",
                    latencyMs = LATENCY_MS,
                    image = null,
                    restartCount = null,
                    containerCreatedAt = null
                )
            }
        }

        `when`("its healthcheck has failed more than once in a row") {
            val error = inspected(
                DockerContainerStatus.RUNNING,
                health = DockerHealthStatus.UNHEALTHY,
                failingStreak = 3,
            ).downError()

            then("the reason should report the streak") {
                error shouldBe "The container is running, but its healthcheck has failed 3 times in a row"
            }
        }

        // The first failure is what flips the daemon's verdict, so a streak of one adds nothing to the reason
        `when`("its healthcheck has failed exactly once") {
            val error = inspected(
                DockerContainerStatus.RUNNING,
                health = DockerHealthStatus.UNHEALTHY,
                failingStreak = 1,
            ).downError()

            then("the reason should omit the streak") {
                error shouldBe "The container is running, but its healthcheck reports it unhealthy"
            }
        }
    }

    given("a container that is not running") {

        `when`("it has exited with a code") {
            val error = inspected(DockerContainerStatus.EXITED, exitCode = 137).downError()

            then("the reason should carry the exit code") {
                error shouldBe "The container exited (137)"
            }
        }

        `when`("it has exited with a code after being OOM killed") {
            val error = inspected(DockerContainerStatus.EXITED, exitCode = 137, oomKilled = true).downError()

            then("the reason should carry both the exit code and the OOM kill") {
                error shouldBe "The container exited (137) after it was OOM killed"
            }
        }

        `when`("it has exited without a reported code") {
            val error = inspected(DockerContainerStatus.EXITED).downError()

            then("the reason should drop the code instead of inventing one") {
                error shouldBe "The container exited"
            }
        }

        `when`("it has exited without a reported code after being OOM killed") {
            val error = inspected(DockerContainerStatus.EXITED, oomKilled = true).downError()

            then("the reason should still report the OOM kill") {
                error shouldBe "The container exited after it was OOM killed"
            }
        }

        mapOf(
            DockerContainerStatus.CREATED to "The container has been created, but never started",
            DockerContainerStatus.PAUSED to "The container is paused",
            DockerContainerStatus.RESTARTING to "The container is restarting",
            DockerContainerStatus.REMOVING to "The container is being removed",
            DockerContainerStatus.DEAD to "The container is dead",
        ).forEach { (status, expectedReason) ->
            `when`("its status is $status") {
                val outcome = inspected(status).outcome()

                then("it should be DOWN with the matching reason") {
                    outcome shouldBe DockerCheckOutcome.Down(
                        error = expectedReason,
                        latencyMs = LATENCY_MS,
                        image = null,
                        restartCount = null,
                        containerCreatedAt = null,
                    )
                }
            }
        }

        // A stale health verdict must not overrule the status: the daemon keeps reporting the last healthcheck
        // result after the container stops
        `when`("it has exited but the daemon still reports it healthy") {
            val error = inspected(
                DockerContainerStatus.EXITED,
                health = DockerHealthStatus.HEALTHY,
                exitCode = 0,
            ).downError()

            then("it should still be DOWN because of the status") {
                error shouldBe "The container exited (0)"
            }
        }
    }

    given("an inspection that did not find the container") {
        `when`("the daemon answered 404") {
            val outcome = DockerInspectResult.ContainerNotFound(LATENCY_MS).outcome()

            then("it should be DOWN naming both the container and the host") {
                outcome shouldBe DockerCheckOutcome.Down(
                    error = """There is no container called "$CONTAINER" on the Docker host "$DOCKER_HOST"""",
                    latencyMs = LATENCY_MS,
                    image = null,
                    restartCount = null,
                    containerCreatedAt = null,
                )
            }
        }
    }

    given("a daemon that answered with an error") {

        `when`("it sent a message along") {
            val outcome = DockerInspectResult.DaemonError(500, "something broke", LATENCY_MS).outcome()

            then("the reason should carry the status code and the message") {
                outcome shouldBe DockerCheckOutcome.Down(
                    error = "The Docker daemon answered 500: something broke",
                    latencyMs = LATENCY_MS,
                    image = null,
                    restartCount = null,
                    containerCreatedAt = null,
                )
            }
        }

        `when`("it sent no message") {
            val error = DockerInspectResult.DaemonError(403, null, LATENCY_MS).downError()

            then("the reason should carry the status code alone") {
                error shouldBe "The Docker daemon answered 403"
            }
        }

        `when`("it sent a blank message") {
            val error = DockerInspectResult.DaemonError(403, "   ", LATENCY_MS).downError()

            then("the reason should fall back to the status code alone") {
                error shouldBe "The Docker daemon answered 403"
            }
        }

        // The message is the daemon's, so it is external input like any other error body
        `when`("its message carries control characters") {
            val error = DockerInspectResult.DaemonError(500, "bro\u0000ke\u0007", LATENCY_MS).downError()

            then("the reason should be sanitized") {
                error shouldBe "The Docker daemon answered 500: bronullke"
            }
        }
    }

    given("a daemon that could not be reached") {

        `when`("the transport failed") {
            val outcome = DockerInspectResult.Unreachable("Connection refused").outcome()

            then("it should be DOWN naming the host and the cause") {
                outcome shouldBe DockerCheckOutcome.Down(
                    error = """The Docker host "$DOCKER_HOST" cannot be reached: Connection refused""",
                    latencyMs = null,
                    image = null,
                    restartCount = null,
                    containerCreatedAt = null,
                )
            }
        }

        // There was no answer to time, unlike every other DOWN branch
        `when`("it is asked for a latency") {
            val outcome = DockerInspectResult.Unreachable("Connection refused").outcome()

            then("it should have none") {
                outcome.latencyMs shouldBe null
            }
        }

        `when`("the failure description is longer than an error may be") {
            val error = DockerInspectResult.Unreachable("x".repeat(300)).downError()

            then("it should be truncated as an error") {
                error shouldEndWith "... [REDACTED]"
            }
        }
    }

    given("the image of the container") {

        `when`("the container is up") {
            val outcome = inspected(DockerContainerStatus.RUNNING, image = "nginx:1.27").outcome()

            then("it should be carried by the outcome") {
                outcome shouldBe DockerCheckOutcome.Up(
                    latencyMs = LATENCY_MS,
                    image = "nginx:1.27",
                    restartCount = null,
                    containerCreatedAt = null,
                )
            }
        }

        `when`("the container is down") {
            val outcome = inspected(DockerContainerStatus.EXITED, exitCode = 1, image = "nginx:1.27").outcome()

            then("it should be carried by the outcome") {
                outcome.shouldBeInstanceOf<DockerCheckOutcome.Down>().image shouldBe "nginx:1.27"
            }
        }

        `when`("the container could not be inspected") {
            val outcome = DockerInspectResult.ContainerNotFound(LATENCY_MS).outcome()

            then("it should be unknown") {
                outcome.image shouldBe null
            }
        }
    }

    given("the restart count and the creation time of the container") {
        val createdAt = OffsetDateTime.parse("2026-10-07T06:59:12.914442Z")

        `when`("the container is up") {
            val outcome = inspected(DockerContainerStatus.RUNNING, restartCount = 3, createdAt = createdAt).outcome()

            then("they should be carried by the outcome") {
                outcome shouldBe DockerCheckOutcome.Up(
                    LATENCY_MS,
                    restartCount = 3,
                    containerCreatedAt = createdAt,
                    image = null,
                )
            }
        }

        `when`("the container is down") {
            val outcome = inspected(DockerContainerStatus.RESTARTING, restartCount = 4, createdAt = createdAt).outcome()

            then("they should be carried by the outcome") {
                val down = outcome.shouldBeInstanceOf<DockerCheckOutcome.Down>()
                down.restartCount shouldBe 4
                down.containerCreatedAt shouldBe createdAt
            }
        }

        `when`("the container could not be inspected") {
            val outcome = DockerInspectResult.Unreachable("connection refused").outcome()

            then("they should be unknown") {
                outcome.restartCount shouldBe null
                outcome.containerCreatedAt shouldBe null
            }
        }
    }
})
