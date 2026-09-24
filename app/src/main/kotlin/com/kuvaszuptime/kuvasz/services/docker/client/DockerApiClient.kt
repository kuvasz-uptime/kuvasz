package com.kuvaszuptime.kuvasz.services.docker.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.DockerContainer
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerListing
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerState
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import com.kuvaszuptime.kuvasz.services.docker.DockerStatsResult
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import jakarta.inject.Singleton
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The Engine API endpoints the checker needs, on top of a [DockerHttpTransport]: the container inspection every
 * check runs, and the resource sampling that only monitors with a metrics history ask for. Both are read-only GETs,
 * which is the reason no Docker client library is pulled in for them.
 *
 * Every call is pinned to [API_VERSION]: calling the API without a version prefix is deprecated, and 1.40 (Engine
 * 19.03) is both the oldest version Docker still supports and the minimum current daemons accept. Every field read
 * here already exists in it, and pinning keeps the response format the same no matter how new the daemon is.
 */
@Singleton
@Requires(bean = DockerHostConfig::class)
class DockerApiClient(private val transport: DockerHttpTransport) {

    private companion object {
        const val API_VERSION = "v1.40"
        const val LIST_PATH = "/$API_VERSION/containers/json?all=true"
    }

    private val objectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun inspectContainer(host: DockerHost, container: String, timeoutMs: Int): DockerInspectResult {
        val start = System.nanoTime()
        return callDaemon(DockerInspectResult::Unreachable) {
            transport.get(host, inspectPath(container), timeoutMs).toInspectResult(elapsedMsSince(start))
        }
    }

    /**
     * The three endpoints differ only in what they return and in how they name a failure to reach the daemon, so
     * the interruption handling - which has to restore the flag the exception cleared - lives here once.
     */
    private fun <T> callDaemon(onUnreachable: (String) -> T, call: () -> T): T =
        try {
            call()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            onUnreachable(ex.describe())
        } catch (ex: IOException) {
            onUnreachable(ex.describe())
        }

    /**
     * A single resource sample of a container, taken without opening the endpoint's stream.
     *
     * `stream=false` is the only way to ask for one on the pinned version, since `one-shot` arrived in v1.41, and it
     * is the better one anyway: the daemon answers after a second collection cycle, so `precpu_stats` is populated
     * and the CPU percentage follows from that one response instead of from state kept between checks. The cost is
     * that the call blocks for the daemon's collection interval (a second, give or take), which comes out of the
     * monitor's timeout budget - and which makes this round-trip useless as a latency signal, unlike the
     * inspection's.
     */
    fun containerStats(host: DockerHost, container: String, timeoutMs: Int): DockerStatsResult =
        callDaemon(DockerStatsResult::Unavailable) {
            transport.get(host, statsPath(container), timeoutMs).toStatsResult()
        }

    /**
     * Every container the daemon knows about, stopped ones included, which is what the UI offers to pick from.
     *
     * Unlike the other two calls this one is not part of a check: it backs the container picker of the upsert form,
     * so a daemon that cannot be reached or a proxy that only allows the inspect endpoint is not an error worth
     * failing on, only an empty list the operator can type past.
     */
    fun listContainers(host: DockerHost, timeoutMs: Int): DockerContainerListing =
        callDaemon(DockerContainerListing::Unavailable) {
            transport.get(host, LIST_PATH, timeoutMs).toListing()
        }

    private fun DockerHttpResponse.toListing(): DockerContainerListing =
        if (statusCode == HttpStatus.OK.code) {
            runCatching { objectMapper.readValue<List<ContainerSummaryNode>>(body) }
                .getOrNull()
                ?.let { nodes -> DockerContainerListing.Listed(nodes.mapNotNull { it.toContainer() }) }
                ?: DockerContainerListing.Unavailable("the response could not be parsed")
        } else {
            DockerContainerListing.Unavailable(
                listOfNotNull("the daemon answered $statusCode", parseErrorMessage(body)).joinToString(": "),
            )
        }

    /**
     * The API reports names with a leading slash and allows several per container; the first is the one the daemon
     * itself shows, so that is what a monitor should reference. A container with no name at all is not offerable.
     */
    private fun ContainerSummaryNode.toContainer(): DockerContainer? {
        val containerId = id?.takeIf { it.isNotBlank() }
        val name = names.orEmpty().firstOrNull()?.removePrefix("/")?.takeIf { it.isNotBlank() }

        return if (containerId != null && name != null) {
            DockerContainer(name = name, id = containerId, image = image, state = state)
        } else {
            null
        }
    }

    private fun DockerHttpResponse.toInspectResult(latencyMs: Int): DockerInspectResult = when (statusCode) {
        HttpStatus.OK.code -> parseState(body)
            ?.let { state -> DockerInspectResult.Inspected(state, latencyMs) }
            ?: DockerInspectResult.DaemonError(statusCode, "the response could not be parsed", latencyMs)

        HttpStatus.NOT_FOUND.code -> DockerInspectResult.ContainerNotFound(latencyMs)
        else -> DockerInspectResult.DaemonError(statusCode, parseErrorMessage(body), latencyMs)
    }

    /**
     * Sampling never decides whether a monitor is up, so every failure - a container that has stopped since, a proxy
     * that only allows the inspect endpoint, a daemon that broke - collapses into a single branch with a reason to
     * log.
     */
    private fun DockerHttpResponse.toStatsResult(): DockerStatsResult =
        if (statusCode == HttpStatus.OK.code) {
            DockerStatsParser.parse(objectMapper, body)
                ?.let { DockerStatsResult.Measured(it) }
                ?: DockerStatsResult.Unavailable("the response carried no readable CPU or memory counters")
        } else {
            DockerStatsResult.Unavailable(
                listOfNotNull("the daemon answered $statusCode", parseErrorMessage(body)).joinToString(": "),
            )
        }

    /**
     * Returns null for anything the pinned API version does not describe - a missing `State`, or a status outside its
     * documented vocabulary - which the caller reports as an unparseable response.
     */
    private fun parseState(body: String): DockerContainerState? {
        val state = runCatching { objectMapper.readValue<InspectResponse>(body) }.getOrNull()?.state ?: return null
        val status = DockerContainerStatus.fromIdentifier(state.status)

        // Healthchecks only run while a container is running, but the daemon keeps reporting the last result after it
        // stops, so outside of that the health is stale and dropped
        val health = state.health?.takeIf { status == DockerContainerStatus.RUNNING }
        // The daemon leaves `Health` out altogether for a container without a healthcheck
        val healthStatus = if (health?.status == null) {
            DockerHealthStatus.NONE
        } else {
            DockerHealthStatus.fromIdentifier(health.status)
        }

        return if (status != null && healthStatus != null) {
            DockerContainerState(
                status = status,
                health = healthStatus,
                exitCode = state.exitCode,
                oomKilled = state.oomKilled == true,
                failingStreak = health?.failingStreak,
            )
        } else {
            null
        }
    }

    private fun parseErrorMessage(body: String): String? =
        runCatching { objectMapper.readValue<DaemonErrorResponse>(body) }
            .getOrNull()
            ?.message
            ?.takeIf { it.isNotBlank() }

    /**
     * The API itself reports names with a leading slash (`/my-app`), which the daemon would answer with a redirect once
     * encoded, so that form is accepted too.
     */
    private fun containerPath(container: String): String {
        val encoded = URLEncoder.encode(container.removePrefix("/"), StandardCharsets.UTF_8).replace("+", "%20")
        return "/$API_VERSION/containers/$encoded"
    }

    private fun inspectPath(container: String): String = "${containerPath(container)}/json"

    private fun statsPath(container: String): String = "${containerPath(container)}/stats?stream=false"

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class InspectResponse(@param:JsonProperty("State") val state: StateNode?)

    private data class StateNode(
        @param:JsonProperty("Status")
        val status: String?,
        @param:JsonProperty("ExitCode")
        val exitCode: Int?,
        @param:JsonProperty("OOMKilled")
        val oomKilled: Boolean?,
        @param:JsonProperty("Health")
        val health: HealthNode?,
    )

    private data class HealthNode(
        @param:JsonProperty("Status") val status: String?,
        @param:JsonProperty("FailingStreak") val failingStreak: Int?,
    )

    private data class ContainerSummaryNode(
        @param:JsonProperty("Id")
        val id: String?,
        @param:JsonProperty("Names")
        val names: List<String>?,
        @param:JsonProperty("Image")
        val image: String?,
        @param:JsonProperty("State")
        val state: String?,
    )

    private data class DaemonErrorResponse(@param:JsonProperty("message") val message: String?)
}
