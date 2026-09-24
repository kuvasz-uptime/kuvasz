package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerListing
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerDaemonAddress
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import com.kuvaszuptime.kuvasz.services.docker.DockerStatsResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
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

private class FakeTransport(private val handler: (path: String) -> DockerHttpResponse) : DockerHttpTransport {
    val paths = mutableListOf<String>()
    val lastPath: String? get() = paths.lastOrNull()
    var lastTimeoutMs: Int? = null

    override fun get(host: DockerHost, path: String, timeoutMs: Int): DockerHttpResponse {
        paths += path
        lastTimeoutMs = timeoutMs
        return handler(path)
    }
}

private fun clientReturning(statusCode: Int, body: String): Pair<DockerApiClient, FakeTransport> {
    val transport = FakeTransport { DockerHttpResponse(statusCode = statusCode, body = body) }
    return DockerApiClient(transport) to transport
}

private fun clientFailingWith(exception: Throwable): DockerApiClient =
    DockerApiClient(FakeTransport { throw exception })

private fun inspectBody(state: String) = """{"Id":"abc123","Name":"/my-app","State":$state}"""

private val RUNNING_BODY = inspectBody("""{"Status":"running","ExitCode":0,"OOMKilled":false}""")

private fun pingResponse(apiVersion: String?) =
    DockerHttpResponse(200, "OK", listOfNotNull(apiVersion?.let { "api-version" to it }).toMap())

/**
 * A daemon that reports [apiVersion] on `/_ping` and answers every versioned call with [answer], so a spec can tell
 * the ping and the call apart by path.
 */
private fun negotiatingClient(
    apiVersion: String?,
    answer: (path: String) -> DockerHttpResponse = { DockerHttpResponse(200, RUNNING_BODY) },
): Pair<DockerApiClient, FakeTransport> {
    val transport = FakeTransport { path -> if (path == "/_ping") pingResponse(apiVersion) else answer(path) }
    return DockerApiClient(transport) to transport
}

// `online_cpus` sits beside the usage block, `percpu_usage` inside it - the two places a CPU count can come from
private fun cpuNode(totalUsage: Long, systemUsage: Long, onlineCpus: String, perCpu: String) =
    """{"cpu_usage":{"total_usage":$totalUsage$perCpu},"system_cpu_usage":$systemUsage$onlineCpus}"""

private const val TWO_ONLINE_CPUS = ""","online_cpus":2"""

/** A sample halfway through the interval: 0.1s of container time against 2s of system time, per CPU. */
private fun statsBody(memoryStats: String, onlineCpus: String = TWO_ONLINE_CPUS, perCpu: String = "") =
    """{"read":"2026-09-18T10:00:00Z","id":"abc123","name":"/my-app",
       "cpu_stats":${cpuNode(200_000_000, 4_000_000_000, onlineCpus, perCpu)},
       "precpu_stats":${cpuNode(100_000_000, 2_000_000_000, onlineCpus, perCpu)},
       "memory_stats":$memoryStats}"""

// A cgroup v1 host reports the plain keys *and* the `total_`-prefixed sums, which is the whole reason the prefixed
// one has to be looked for first. The two inactive-file figures differ here so a mix-up cannot pass.
private const val CGROUP_V1_MEMORY = """{"usage":52428800,"max_usage":60000000,"limit":2097152000,
    "stats":{"cache":10485760,"rss":41943040,"inactive_file":8388608,"total_inactive_file":10485760,
             "total_cache":10485760,"total_rss":41943040}}"""

private const val CGROUP_V2_MEMORY = """{"usage":52428800,"limit":2097152000,
    "stats":{"anon":41943040,"file":10485760,"inactive_file":8388608,"active_file":2097152}}"""

// Captured from Engine 29.8.0 on a cgroup v2 host over the `/v1.40/` path, with a busy loop saturating one of
// the container's two cores. Trimmed to the fields the parser reads; note there is no `total_inactive_file` and no
// `percpu_usage`, which is what a v2 host looks like.
private const val LIVE_CGROUP_V2_SAMPLE = """{
    "cpu_stats":{"cpu_usage":{"total_usage":116646004000},"system_cpu_usage":242511270000000,"online_cpus":2},
    "precpu_stats":{"cpu_usage":{"total_usage":115640755000},"system_cpu_usage":242509260000000,"online_cpus":2},
    "memory_stats":{"usage":92123136,"limit":2525110272,
        "stats":{"anon":4591616,"file":81563648,"inactive_file":3452928,"active_file":53661696,"slab":4660976}}}"""

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

            then("the inspect endpoint should have been called on the negotiated version, with the monitor's timeout") {
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

        `when`("the daemon reports a status outside the supported versions' vocabulary") {
            val (client, _) = clientReturning(200, inspectBody("""{"Status":"hibernating"}"""))
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the daemon broke its own contract, so the payload counts as unparseable") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.message shouldBe "the response could not be parsed"
            }
        }

        `when`("a running container reports a health status outside the supported versions' vocabulary") {
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

        `when`("the daemon no longer supports the negotiated API version") {
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

    given("a daemon that answers a resource sampling") {

        `when`("the host runs cgroup v1") {
            val (client, transport) = clientReturning(200, statsBody(CGROUP_V1_MEMORY))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the version should be read off the payload, since the oldest supported version cannot be asked") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cgroupVersion shouldBe DockerCgroupVersion.V1
            }

            then("the prefixed inactive file pages should come off the usage, not the plain ones v1 also reports") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryUsageBytes shouldBe 41_943_040
                result.stats.memoryLimitBytes shouldBe 2_097_152_000
            }

            then("the CPU percentage should be scaled by the online CPUs, the way docker stats reports it") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe (10.0 plusOrMinus 0.0001)
            }

            then("the stream should be turned off, so the daemon answers once instead of forever") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/stats?stream=false"
                transport.lastTimeoutMs shouldBe TIMEOUT_MS
            }
        }

        `when`("the host runs cgroup v2") {
            val (client, _) = clientReturning(200, statsBody(CGROUP_V2_MEMORY, onlineCpus = ""","online_cpus":4"""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the absence of the prefixed key should settle the version") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cgroupVersion shouldBe DockerCgroupVersion.V2
            }

            then("the plain inactive file pages should come off the usage") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryUsageBytes shouldBe 44_040_192
                result.stats.cpuUsagePercent shouldBe (20.0 plusOrMinus 0.0001)
            }
        }

        `when`("the daemon is too old to report the online CPU count") {
            val (client, _) = clientReturning(
                200,
                statsBody(
                    CGROUP_V1_MEMORY,
                    onlineCpus = "",
                    perCpu = ""","percpu_usage":[50000000,50000000,50000000,50000000]""",
                ),
            )
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the per-CPU array should stand in for it, as the API's own documentation prescribes") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe (20.0 plusOrMinus 0.0001)
            }
        }

        `when`("the daemon reports an online CPU count of zero") {
            val (client, _) = clientReturning(
                200,
                statsBody(
                    CGROUP_V1_MEMORY,
                    onlineCpus = ""","online_cpus":0""",
                    perCpu = ""","percpu_usage":[50000000,50000000,50000000,50000000]""",
                ),
            )
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should fall back to the per-CPU array, the way an absent count does in the cli") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe (20.0 plusOrMinus 0.0001)
            }
        }

        `when`("neither the online CPU count nor the per-CPU array is reported") {
            val (client, _) = clientReturning(200, statsBody(CGROUP_V1_MEMORY, ""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the CPU should be left out, while the memory the host did account for is kept") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent.shouldBeNull()
                result.stats.memoryUsageBytes shouldBe 41_943_040
            }
        }

        `when`("the container burned no CPU between the two reads") {
            val idle = """{"read":"2026-09-18T10:00:00Z",
                "cpu_stats":${cpuNode(100_000_000, 2_000_000_000, TWO_ONLINE_CPUS, "")},
                "precpu_stats":${cpuNode(100_000_000, 2_000_000_000, TWO_ONLINE_CPUS, "")},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, idle)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be recorded as idle, which is a measurement rather than a missing one") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe 0.0
            }
        }

        `when`("the daemon reports an inactive file figure larger than the usage it is part of") {
            val nonsense = """{"usage":1000,"limit":2000,"stats":{"total_inactive_file":5000}}"""
            val (client, _) = clientReturning(200, statsBody(nonsense))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the raw usage should stand, rather than an underflowed one") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryUsageBytes shouldBe 1000
            }
        }

        `when`("the container has no memory limit set") {
            val unlimited = """{"usage":1000,"limit":0,"stats":{"inactive_file":100}}"""
            val (client, _) = clientReturning(200, statsBody(unlimited))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the unaccountable limit should be left out instead of recorded as zero") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryLimitBytes.shouldBeNull()
                result.stats.memoryUsageBytes shouldBe 900
            }
        }

        `when`("a real daemon answers, one core of two pegged") {
            val (client, _) = clientReturning(200, LIVE_CGROUP_V2_SAMPLE)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the figures should be the ones docker stats itself printed for that very sample") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cgroupVersion shouldBe DockerCgroupVersion.V2
                // `docker stats` reported 84.79MiB / 3.52% against the same container
                result.stats.memoryUsageBytes shouldBe 88_670_208
                result.stats.memoryLimitBytes shouldBe 2_525_110_272
                // A single saturated core out of two, which is how docker stats counts to 100%
                result.stats.cpuUsagePercent shouldBe (100.02 plusOrMinus 0.01)
            }
        }

        `when`("the container name carries the leading slash the API itself reports names with") {
            val (client, transport) = clientReturning(200, statsBody(CGROUP_V2_MEMORY))
            client.containerStats(LOCAL_HOST, "/my-app", TIMEOUT_MS)

            then("it should be normalised the same way the inspection normalises it") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/stats?stream=false"
            }
        }
    }

    given("a daemon that answers a sampling with only part of the counters") {

        `when`("the payload carries no memory block at all") {
            val (client, _) = clientReturning(200, statsBody("null"))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("there is no cgroup to identify, so nothing is recorded") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
            }
        }

        `when`("the memory block names its cgroup but carries no usage") {
            val (client, _) = clientReturning(200, statsBody("""{"limit":2000,"stats":{"inactive_file":100}}"""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the CPU should still be recorded, with the memory left out") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cgroupVersion shouldBe DockerCgroupVersion.V2
                result.stats.memoryUsageBytes.shouldBeNull()
                result.stats.cpuUsagePercent shouldBe (10.0 plusOrMinus 0.0001)
            }
        }

        `when`("the container has no memory limit reported at all") {
            val (client, _) = clientReturning(200, statsBody("""{"usage":1000,"stats":{"inactive_file":100}}"""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the usage should stand on its own") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryUsageBytes shouldBe 900
                result.stats.memoryLimitBytes.shouldBeNull()
            }
        }

        `when`("the memory block holds counters but none that name a cgroup hierarchy") {
            val (client, _) = clientReturning(200, statsBody("""{"usage":1000,"stats":{"anon":500}}"""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the accounting cannot be read without knowing which key holds the page cache") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
            }
        }

        `when`("the CPU block holds no usage node") {
            val noUsage = """{"cpu_stats":{"system_cpu_usage":4000000000,"online_cpus":2},
                "precpu_stats":{"system_cpu_usage":2000000000,"online_cpus":2},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, noUsage)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("there is no container share to scale, so the CPU is left out") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent.shouldBeNull()
                result.stats.memoryUsageBytes shouldBe 44_040_192
            }
        }

        `when`("the per-CPU array the daemon falls back to is empty") {
            val (client, _) = clientReturning(
                200,
                statsBody(CGROUP_V1_MEMORY, onlineCpus = "", perCpu = ""","percpu_usage":[]"""),
            )
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should read as idle, which is where the cli's multiplication by zero lands too") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe 0.0
            }
        }

        `when`("the payload carries no CPU block at all") {
            val (client, _) = clientReturning(200, """{"memory_stats":$CGROUP_V2_MEMORY}""")
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the memory should still be recorded, with the CPU left out") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.memoryUsageBytes shouldBe 44_040_192
                result.stats.cpuUsagePercent.shouldBeNull()
            }
        }

        `when`("there is no previous read to compare against") {
            // What `one-shot=true` answers with on API v1.41 and up, where the daemon skips the second cycle
            val oneShot = """{"cpu_stats":${cpuNode(200_000_000, 4_000_000_000, TWO_ONLINE_CPUS, "")},
                "precpu_stats":{"cpu_usage":{"total_usage":0}},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, oneShot)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("no CPU percentage should be invented from the missing baseline") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent.shouldBeNull()
                result.stats.memoryUsageBytes shouldBe 44_040_192
            }
        }

        `when`("the CPU block reports no system usage, as it does for a Windows container") {
            val noSystem = """{"cpu_stats":{"cpu_usage":{"total_usage":200000000},"online_cpus":2},
                "precpu_stats":{"cpu_usage":{"total_usage":100000000},"online_cpus":2},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, noSystem)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the container share cannot be scaled against the host, so the CPU is left out") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent.shouldBeNull()
            }
        }

        `when`("the container's own counter went backwards, as it does across a restart") {
            val restarted = """{"cpu_stats":${cpuNode(50_000_000, 4_000_000_000, TWO_ONLINE_CPUS, "")},
                "precpu_stats":${cpuNode(100_000_000, 2_000_000_000, TWO_ONLINE_CPUS, "")},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, restarted)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the negative delta should read as idle rather than as a negative percentage") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe 0.0
            }
        }

        `when`("the host's own counter went backwards, as it does across a reboot") {
            val rebooted = """{"cpu_stats":${cpuNode(200_000_000, 1_000_000_000, TWO_ONLINE_CPUS, "")},
                "precpu_stats":${cpuNode(100_000_000, 2_000_000_000, TWO_ONLINE_CPUS, "")},
                "memory_stats":$CGROUP_V2_MEMORY}"""
            val (client, _) = clientReturning(200, rebooted)
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the same should hold, instead of dividing by a negative system delta") {
                result.shouldBeInstanceOf<DockerStatsResult.Measured>()
                result.stats.cpuUsagePercent shouldBe 0.0
            }
        }
    }

    given("a daemon that cannot produce a sample") {

        `when`("the container stopped between the inspection and the sampling") {
            // The daemon answers 200 with an empty memory block for a container that is no longer running
            val (client, _) = clientReturning(200, statsBody("{}", ""))
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("there should be no cgroup to read, so nothing is recorded") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
                result.reason shouldBe "the response carried no readable CPU or memory counters"
            }
        }

        `when`("the body is not JSON") {
            val (client, _) = clientReturning(200, "<html>nope</html>")
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be reported as an unavailable sample") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
            }
        }

        `when`("a socket proxy allows the inspect endpoint but not this one") {
            val (client, _) = clientReturning(403, "Forbidden")
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the status code alone should carry the reason") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
                result.reason shouldBe "the daemon answered 403"
            }
        }

        `when`("the daemon explains itself") {
            val (client, _) = clientReturning(404, """{"message":"No such container: my-app"}""")
            val result = client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("its own message should be appended to the status code") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
                result.reason shouldBe "the daemon answered 404: No such container: my-app"
            }
        }

        `when`("the sampling outlives the monitor's timeout") {
            val result = clientFailingWith(IOException("the request timed out after 5000ms"))
                .containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be unavailable, carrying the transport's reason") {
                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
                result.reason shouldBe "the request timed out after 5000ms"
            }
        }

        `when`("the sampling is interrupted") {
            then("it should be unavailable and the interrupt flag restored") {
                // Asserted in the same block that raises the flag, so it is cleared on this very thread
                val result = clientFailingWith(InterruptedException("interrupted"))
                    .containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerStatsResult.Unavailable>()
                Thread.interrupted() shouldBe true
            }
        }
    }

    given("a daemon that answers a container listing") {

        `when`("it reports containers") {
            val (client, transport) = clientReturning(
                200,
                """
                [
                  {"Id": "abc123", "Names": ["/my-app"], "Image": "nginx:alpine", "State": "running"},
                  {"Id": "def456", "Names": ["/worker", "/worker-alias"], "Image": "busybox", "State": "exited"}
                ]
                """.trimIndent(),
            )

            val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

            then("it should ask for the stopped ones too, since a monitor may watch one") {
                transport.lastPath shouldBe "/v1.40/containers/json?all=true"
            }

            then("it should report them with the leading slash stripped from the name") {
                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                result.containers.map { it.name } shouldBe listOf("my-app", "worker")
            }

            then("it should carry the id, the image and the state of each") {
                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                val first = result.containers.first()
                first.id shouldBe "abc123"
                first.image shouldBe "nginx:alpine"
                first.state shouldBe "running"
            }

            // The API allows several names per container and the first is the one the daemon itself shows
            then("it should take only the first name of a container that has more") {
                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                result.containers.last().name shouldBe "worker"
            }
        }

        `when`("the daemon reports no container at all") {
            val (client, _) = clientReturning(200, "[]")

            then("it should report an empty listing rather than a failure") {
                val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                result.containers.shouldBeEmpty()
            }
        }

        `when`("an entry carries no usable name") {
            val (client, _) = clientReturning(
                200,
                """[{"Id": "abc123", "Names": []}, {"Id": "def456", "Names": ["/named"]}]""",
            )

            then("it should drop that entry and keep the rest") {
                val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                result.containers.map { it.name } shouldBe listOf("named")
            }
        }

        `when`("an entry carries no id") {
            val (client, _) = clientReturning(200, """[{"Names": ["/nameless"]}]""")

            then("it should drop it, because a monitor could not reference it") {
                val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Listed>()
                result.containers.shouldBeEmpty()
            }
        }
    }

    given("a daemon that cannot produce a listing") {

        // A socket proxy that only allows the inspect endpoint answers this way
        `when`("it rejects the request") {
            val (client, _) = clientReturning(403, """{"message": "access denied"}""")

            then("it should be unavailable, carrying the status and the message") {
                val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Unavailable>()
                result.reason shouldBe "the daemon answered 403: access denied"
            }
        }

        `when`("it answers something unparseable") {
            val (client, _) = clientReturning(200, "not json at all")

            then("it should be unavailable") {
                val result = client.listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Unavailable>()
                result.reason shouldBe "the response could not be parsed"
            }
        }

        `when`("it cannot be reached") {
            val result = clientFailingWith(IOException("connection refused"))
                .listContainers(LOCAL_HOST, TIMEOUT_MS)

            then("it should be unavailable, carrying the transport's reason") {
                result.shouldBeInstanceOf<DockerContainerListing.Unavailable>()
                result.reason shouldBe "connection refused"
            }
        }

        `when`("the listing is interrupted") {
            then("it should be unavailable and the interrupt flag restored") {
                val result = clientFailingWith(InterruptedException("interrupted"))
                    .listContainers(LOCAL_HOST, TIMEOUT_MS)

                result.shouldBeInstanceOf<DockerContainerListing.Unavailable>()
                Thread.interrupted() shouldBe true
            }
        }
    }

    given("a daemon whose API version has to be negotiated") {

        `when`("it speaks a newer version than the client uses") {
            val (client, transport) = negotiatingClient("1.55")
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should be pinged first, and called on the newest version the client uses") {
                transport.paths shouldBe listOf("/_ping", "/v1.44/containers/my-app/json")
            }
        }

        `when`("it speaks a version between the oldest supported and the newest used") {
            val (client, transport) = negotiatingClient("1.41")
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("its own version should be used") {
                transport.lastPath shouldBe "/v1.41/containers/my-app/json"
            }
        }

        // Compared as numbers: as text, 1.9 would sort after 1.40
        `when`("it speaks a version older than the oldest supported") {
            val (client, transport) = negotiatingClient("1.9")
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the oldest supported version should be used, for the daemon to reject with its own explanation") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/json"
            }
        }

        `when`("it does not tell its version") {
            val (client, transport) = negotiatingClient(apiVersion = null)
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the oldest supported version should be used") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/json"
            }
        }

        `when`("it tells a version that cannot be parsed") {
            val (client, transport) = negotiatingClient("latest")
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the oldest supported version should be used") {
                transport.lastPath shouldBe "/v1.40/containers/my-app/json"
            }
        }

        // A socket proxy that does not allow the ping answers it this way
        `when`("the ping is rejected") {
            val transport = FakeTransport { path ->
                if (path == "/_ping") DockerHttpResponse(403, "Forbidden") else DockerHttpResponse(200, RUNNING_BODY)
            }
            val client = DockerApiClient(transport)
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the oldest supported version should be used and settled, not asked for on every check") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                transport.paths shouldBe listOf(
                    "/_ping",
                    "/v1.40/containers/my-app/json",
                    "/v1.40/containers/my-app/json",
                )
            }
        }

        `when`("every endpoint is called several times") {
            val (client, transport) = negotiatingClient("1.44") { path ->
                DockerHttpResponse(200, if (path.endsWith("?all=true")) "[]" else RUNNING_BODY)
            }
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)
            client.containerStats(LOCAL_HOST, "my-app", TIMEOUT_MS)
            client.listContainers(LOCAL_HOST, TIMEOUT_MS)

            then("the version should be negotiated only once per host, and used by all of them") {
                transport.paths shouldBe listOf(
                    "/_ping",
                    "/v1.44/containers/my-app/json",
                    "/v1.44/containers/my-app/stats?stream=false",
                    "/v1.44/containers/json?all=true",
                )
            }
        }

        `when`("the ping cannot reach the daemon") {
            var reachable = false
            val transport = FakeTransport { path ->
                when {
                    !reachable -> throw IOException("connection refused")
                    path == "/_ping" -> pingResponse("1.44")
                    else -> DockerHttpResponse(200, RUNNING_BODY)
                }
            }
            val client = DockerApiClient(transport)
            val unreachable = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)
            reachable = true
            val inspected = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the check should report the daemon unreachable") {
                unreachable.shouldBeInstanceOf<DockerInspectResult.Unreachable>()
                unreachable.error shouldBe "connection refused"
            }

            then("nothing should be settled, so the next check negotiates again") {
                inspected.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                transport.paths shouldBe listOf("/_ping", "/_ping", "/v1.44/containers/my-app/json")
            }
        }

        `when`("the daemon is swapped for one that no longer accepts the settled version") {
            var daemonVersion = "1.44"
            val transport = FakeTransport { path ->
                when {
                    path == "/_ping" -> pingResponse(daemonVersion)
                    path.startsWith("/v$daemonVersion/") || daemonVersion == "1.44" ->
                        DockerHttpResponse(200, RUNNING_BODY)

                    else -> DockerHttpResponse(400, """{"message":"client version 1.44 is too new"}""")
                }
            }
            val client = DockerApiClient(transport)
            client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)
            daemonVersion = "1.43"
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("the version should be negotiated again and the call retried, instead of taking the monitor down") {
                result.shouldBeInstanceOf<DockerInspectResult.Inspected>()
                transport.paths shouldBe listOf(
                    "/_ping",
                    "/v1.44/containers/my-app/json",
                    "/v1.44/containers/my-app/json",
                    "/_ping",
                    "/v1.43/containers/my-app/json",
                )
            }
        }

        `when`("a freshly negotiated version is rejected") {
            val tooOld = "client version 1.40 is too old. Minimum supported API version is 1.44"
            val (client, transport) = negotiatingClient("1.9") {
                DockerHttpResponse(400, """{"message":"$tooOld"}""")
            }
            val result = client.inspectContainer(LOCAL_HOST, "my-app", TIMEOUT_MS)

            then("it should not be retried, and the daemon's explanation should be surfaced") {
                result.shouldBeInstanceOf<DockerInspectResult.DaemonError>()
                result.message shouldBe tooOld
                transport.paths shouldBe listOf("/_ping", "/v1.40/containers/my-app/json")
            }
        }
    }
})
