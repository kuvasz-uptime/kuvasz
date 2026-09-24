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
import java.util.concurrent.ConcurrentHashMap

/**
 * The Engine API endpoints the checker needs, on top of a [DockerHttpTransport]: the container inspection every
 * check runs, and the resource sampling that only monitors with a metrics history ask for. Both are read-only GETs,
 * which is the reason no Docker client library is pulled in for them.
 *
 * Every call carries an explicit API version, because calling the API without one is deprecated. No single version is
 * accepted by every supported daemon, so it is negotiated per host through `/_ping` the way Docker's own client does
 * it, and kept between [DockerApiVersion.OLDEST_SUPPORTED] and [DockerApiVersion.NEWEST_USED], which read the same.
 */
@Singleton
@Requires(bean = DockerHostConfig::class)
class DockerApiClient(private val transport: DockerHttpTransport) {

    private companion object {
        const val PING_PATH = "/_ping"
        const val API_VERSION_HEADER = "api-version"
        const val LIST_PATH = "/containers/json?all=true"
    }

    private val negotiatedVersions = ConcurrentHashMap<String, DockerApiVersion>()

    private val objectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun inspectContainer(host: DockerHost, container: String, timeoutMs: Int): DockerInspectResult =
        callDaemon(DockerInspectResult::Unreachable) {
            val (response, latencyMs) = getVersioned(host, inspectPath(container), timeoutMs)
            response.toInspectResult(latencyMs)
        }

    /**
     * Calls [path] on the version negotiated with the host, pinging it first when there is none yet. The latency is
     * that of the call itself, so a check that happens to negotiate does not report the ping as the daemon's slowness.
     *
     * A cached version goes stale if the daemon is swapped for one that no longer accepts it, which it answers with a
     * 400, so that is re-negotiated and retried once instead of taking the monitor down until the next check. Each
     * exchange gets the whole timeout, since a ping happens once per host and not once per check.
     */
    private fun getVersioned(host: DockerHost, path: String, timeoutMs: Int): TimedResponse {
        val cachedVersion = negotiatedVersions[host.name]
        val response = timedGet(host, (cachedVersion ?: negotiate(host, timeoutMs)).pathPrefix + path, timeoutMs)
        if (cachedVersion == null || response.response.statusCode != HttpStatus.BAD_REQUEST.code) {
            return response
        }
        negotiatedVersions.remove(host.name, cachedVersion)
        return timedGet(host, negotiate(host, timeoutMs).pathPrefix + path, timeoutMs)
    }

    private fun timedGet(host: DockerHost, path: String, timeoutMs: Int): TimedResponse {
        val start = System.nanoTime()
        return TimedResponse(transport.get(host, path, timeoutMs), elapsedMsSince(start))
    }

    /**
     * Whatever the daemon answers is settled for good, a proxy that does not allow `/_ping` included, which falls back
     * to the oldest supported version. Only a daemon that could not be reached at all is asked again next time.
     */
    private fun negotiate(host: DockerHost, timeoutMs: Int): DockerApiVersion {
        val ping = transport.get(host, PING_PATH, timeoutMs)
        val daemonVersion = DockerApiVersion.parse(ping.headers[API_VERSION_HEADER])
        return DockerApiVersion.negotiate(daemonVersion).also { negotiatedVersions[host.name] = it }
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
     * `stream=false` is the only way to ask for one on the oldest supported version, since `one-shot` arrived in
     * v1.41, and it is the better one anyway: the daemon answers after a second collection cycle, so `precpu_stats` is
     * populated and the CPU percentage follows from that one response instead of from state kept between checks. The
     * cost is that the call blocks for the daemon's collection interval (a second, give or take), which comes out of
     * the monitor's timeout budget - and which makes this round-trip useless as a latency signal, unlike the
     * inspection's.
     */
    fun containerStats(host: DockerHost, container: String, timeoutMs: Int): DockerStatsResult =
        callDaemon(DockerStatsResult::Unavailable) {
            getVersioned(host, statsPath(container), timeoutMs).response.toStatsResult()
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
            getVersioned(host, LIST_PATH, timeoutMs).response.toListing()
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
     * Returns null for anything the supported API versions do not describe - a missing `State`, or a status outside its
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
        return "/containers/$encoded"
    }

    private fun inspectPath(container: String): String = "${containerPath(container)}/json"

    private fun statsPath(container: String): String = "${containerPath(container)}/stats?stream=false"

    private fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class TimedResponse(val response: DockerHttpResponse, val latencyMs: Int)

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
