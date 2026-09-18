package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerDaemonAddress
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.nio.file.Path

private val LOCAL_HOST = DockerHost(
    name = "local",
    url = "unix:///var/run/docker.sock",
    address = DockerDaemonAddress.UnixSocket(Path.of("/var/run/docker.sock")),
)

private const val TIMEOUT_MS = 5_000

private class FakeTransport(private val handler: () -> DockerHttpResponse) : DockerHttpTransport {
    var lastPath: String? = null
    var lastTimeoutMs: Int? = null

    override fun get(host: DockerHost, path: String, timeoutMs: Int): DockerHttpResponse {
        lastPath = path
        lastTimeoutMs = timeoutMs
        return handler()
    }
}

private fun clientReturning(statusCode: Int, body: String): Pair<DockerApiClient, FakeTransport> {
    val transport = FakeTransport { DockerHttpResponse(statusCode = statusCode, body = body) }
    return DockerApiClient(transport) to transport
}

private fun clientFailingWith(exception: Throwable): DockerApiClient =
    DockerApiClient(FakeTransport { throw exception })

private fun inspectBody(state: String) = """{"Id":"abc123","Name":"/my-app","State":$state}"""

class DockerApiClientTest : BehaviorSpec({

    given("a daemon that answers a container inspection") {

        `when`("the container is running and healthy") {
            val (client, transport) = clientReturning(
                200,
                inspectBody(
                    """{"Status":"running","Running":true,"Paused":false,"ExitCode":0,"OOMKilled":false,
                       "Health":{"Status":"healthy","FailingStreak":0}}"""
                ),
            )
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the state should be parsed") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                result.state.status shouldBe DockerContainerStatus.RUNNING
                result.state.health shouldBe DockerHealthStatus.HEALTHY
                result.state.exitCode shouldBe 0
                result.state.oomKilled shouldBe false
                result.state.failingStreak shouldBe 0
                result.latencyMs shouldBeGreaterThanOrEqual 0
            }

            then("the inspect endpoint should have been called on the pinned API version, with the monitor's timeout") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/json"
                transport.lastTimeoutMs shouldBe TIMEOUT_MS
            }
        }

        `when`("the container was OOM killed") {
            val (client, _) = clientReturning(
                200,
                inspectBody("""{"Status":"exited","Running":false,"ExitCode":137,"OOMKilled":true}"""),
            )
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the exit code and the OOM flag should be kept for the failure reason") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                result.state.status shouldBe DockerContainerStatus.EXITED
                result.state.exitCode shouldBe 137
                result.state.oomKilled shouldBe true
            }

            then("the missing Health block should read as a container without a healthcheck") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                result.state.health shouldBe DockerHealthStatus.NONE
            }
        }

        `when`("the healthcheck is failing") {
            val (client, _) = clientReturning(
                200,
                inspectBody(
                    """{"Status":"running","Health":{"Status":"unhealthy","FailingStreak":3}}"""
                ),
            )
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the streak should be available for the message") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                result.state.health shouldBe DockerHealthStatus.UNHEALTHY
                result.state.failingStreak shouldBe 3
            }
        }

        `when`("the container exited, but the daemon still reports its last healthcheck result") {
            val (client, _) = clientReturning(
                200,
                inspectBody(
                    """{"Status":"exited","ExitCode":137,"Health":{"Status":"unhealthy","FailingStreak":0}}"""
                ),
            )
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the stale health should be dropped, leaving the exit to speak for itself") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                result.state.status shouldBe DockerContainerStatus.EXITED
                result.state.exitCode shouldBe 137
                result.state.health shouldBe DockerHealthStatus.NONE
                result.state.failingStreak.shouldBeNull()
            }
        }

        `when`("the daemon reports a status outside the pinned version's vocabulary") {
            val (client, _) = clientReturning(200, inspectBody("""{"Status":"hibernating"}"""))
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the daemon broke its own contract, so the payload counts as unparseable") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.message shouldBe "the response could not be parsed"
            }
        }

        `when`("a running container reports a health status outside the pinned version's vocabulary") {
            val (client, _) = clientReturning(
                200,
                inspectBody("""{"Status":"running","Health":{"Status":"dozing"}}"""),
            )
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported the same way as an unknown container status, not thrown out of the client") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.message shouldBe "the response could not be parsed"
            }
        }

        `when`("the container name needs escaping") {
            val (client, transport) = clientReturning(200, inspectBody("""{"Status":"running"}"""))
            client.inspectContainer(LOCAL_HOST, "my app/../etc", TIMEOUT_MS)

            then("the path segment should be encoded") {
                transport.lastPath shouldBe "/v1.40/containers/my%20app%2F..%2Fetc/json"
            }
        }

        `when`("the container name carries the leading slash the API itself reports names with") {
            val (client, transport) = clientReturning(200, inspectBody("""{"Status":"running"}"""))
            client.inspectContainer(LOCAL_HOST, "/my-app", TIMEOUT_MS)

            then("the slash should be dropped, instead of being encoded into a path the daemon redirects") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/json"
            }
        }
    }

    given("a daemon that answers with something unusable") {

        `when`("the body is not JSON") {
            val (client, _) = clientReturning(200, "<html>nope</html>")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported as a daemon error") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.statusCode shouldBe 200
                result.message shouldBe "the response could not be parsed"
            }
        }

        `when`("the payload carries no State at all") {
            val (client, _) = clientReturning(200, """{"Id":"abc123"}""")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported as a daemon error") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
            }
        }
    }

    given("a daemon that rejects the request") {

        `when`("the container does not exist") {
            val (client, _) = clientReturning(404, """{"message":"No such container: my-app"}""")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported as a missing container") {
                result.shouldBeInstanceOf<DockerInspectResult.ContainerNotFound>()
                result.latencyMs shouldBeGreaterThanOrEqual 0
            }
        }

        `when`("the daemon fails internally") {
            val (client, _) = clientReturning(500, """{"message":"driver failed"}""")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the daemon's own message should be surfaced") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.statusCode shouldBe 500
                result.message shouldBe "driver failed"
            }
        }

        `when`("the daemon no longer supports the pinned API version") {
            val tooOld = "client version 1.40 is too old. Minimum supported API version is 1.44, please upgrade your " +
                "client to a newer version"
            val (client, _) = clientReturning(400, """{"message":"$tooOld"}""")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the daemon's own explanation should be surfaced") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.statusCode shouldBe 400
                result.message shouldBe tooOld
            }
        }

        `when`("the error body is not the usual shape") {
            val (client, _) = clientReturning(503, "Service Unavailable")
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the status code should still be reported") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.statusCode shouldBe 503
                result.message.shouldBeNull()
            }
        }
    }

    given("a daemon that cannot be reached") {

        `when`("the transport fails") {
            val result = clientFailingWith(IOException("Connection refused"))
                .inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported as unreachable, carrying the transport's reason") {
                result.shouldBeInstanceOf<DockerInspectResult.Unreachable>()
                result.error shouldBe "Connection refused"
            }
        }

        `when`("the transport fails without a message") {
            val result = clientFailingWith(IOException()).inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the exception type should stand in for it") {
                result.shouldBeInstanceOf<DockerInspectResult.Unreachable>()
                result.error shouldBe "IOException"
            }
        }

        `when`("the check is interrupted") {
            then("it should be reported as unreachable and the interrupt flag restored") {
                // Asserted in the same block that raises the flag, so it is cleared on this very thread
                val result = clientFailingWith(InterruptedException("interrupted"))
                    .inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerInspectResult.Unreachable>()
                Thread.interrupted() shouldBe true
            }
        }
    }
})
