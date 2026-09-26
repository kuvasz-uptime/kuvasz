package com.kuvaszuptime.kuvasz.services.docker

/**
 * The lifecycle states the Docker Engine API can report for a container.
 *
 * `State.Running` and `State.Paused` are not mutually exclusive, and that `Status` is the field to branch on, which
 * is why this is the signal the checker uses.
 */
enum class DockerContainerStatus(val identifier: String) {
    CREATED("created"),
    RUNNING("running"),
    PAUSED("paused"),
    RESTARTING("restarting"),
    REMOVING("removing"),
    EXITED("exited"),
    DEAD("dead");

    companion object {
        /** Null when the daemon reports something outside the pinned API version's vocabulary. */
        fun fromIdentifier(identifier: String?): DockerContainerStatus? =
            identifier?.lowercase()?.let { raw -> entries.firstOrNull { it.identifier == raw } }
    }
}

/**
 * The healthcheck states of a container. `NONE` means there is no health signal to go by: either the container has no
 * healthcheck (the daemon leaves `State.Health` out of the inspect payload), or it is not running, in which case the
 * daemon would still report the last, stale result.
 */
enum class DockerHealthStatus(val identifier: String) {
    NONE("none"),
    STARTING("starting"),
    HEALTHY("healthy"),
    UNHEALTHY("unhealthy");

    companion object {
        /** Null when the daemon reports something outside the pinned API version's vocabulary. */
        fun fromIdentifier(identifier: String?): DockerHealthStatus? =
            identifier?.lowercase()?.let { raw -> entries.firstOrNull { it.identifier == raw } }
    }
}

/**
 * The subset of a container's inspect payload the checker actually needs.
 *
 * Both enums are non-null: every call is pinned to a fixed API version, whose schema enumerates exactly these values,
 * so a value outside them means the daemon broke its own published contract. That is treated as an unparseable
 * payload rather than modeled, which keeps the checker's branches to the states the API actually documents.
 */
data class DockerContainerState(
    val status: DockerContainerStatus,
    val health: DockerHealthStatus,
    val exitCode: Int?,
    val oomKilled: Boolean,
    val failingStreak: Int?,
    val image: String? = null,
)

/**
 * The outcome of a single container inspection. The variants map one-to-one onto the branches the checker needs, so
 * a successful inspection can never carry an error and vice versa.
 */
sealed interface DockerInspectResult {

    data class Inspected(
        val state: DockerContainerState,
        val latencyMs: Int,
    ) : DockerInspectResult

    data class ContainerNotFound(val latencyMs: Int) : DockerInspectResult

    /** The daemon answered, but with an unexpected status code. */
    data class DaemonError(
        val statusCode: Int,
        val message: String?,
        val latencyMs: Int,
    ) : DockerInspectResult

    /** The daemon could not be reached at all: connection refused, TLS failure, timeout, malformed response. */
    data class Unreachable(val error: String) : DockerInspectResult
}
