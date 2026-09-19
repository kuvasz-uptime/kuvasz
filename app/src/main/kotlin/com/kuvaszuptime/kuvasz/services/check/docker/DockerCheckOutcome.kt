package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.sanitizeAsError
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerState
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult

/**
 * The up/down decision of a single Docker check.
 *
 * [latencyMs] is the Docker API round-trip, which is why a [Down] can carry one too: a container that is merely
 * stopped was still reported by a daemon that answered, and that answer is a valid measurement of the daemon. It is
 * only absent when there was no answer at all.
 */
sealed interface DockerCheckOutcome {

    val latencyMs: Int?

    data class Up(override val latencyMs: Int?) : DockerCheckOutcome

    data class Down(val error: String, override val latencyMs: Int?) : DockerCheckOutcome
}

/**
 * Turns an inspection into the monitor's verdict.
 *
 * The branch is on `State.Status` rather than on `State.Running`, because the API's own schema warns that `Running`
 * and `Paused` are not mutually exclusive, so only the status tells the states apart. `ExitCode`, `OOMKilled` and
 * `FailingStreak` are folded into the reason, since "the container exited (137) after it was OOM killed" is what an
 * operator can act on, where a bare "exited" is not.
 */
fun DockerInspectResult.toCheckOutcome(dockerHost: String, container: String): DockerCheckOutcome = when (this) {
    is DockerInspectResult.Inspected -> state.toCheckOutcome(latencyMs)

    is DockerInspectResult.ContainerNotFound -> DockerCheckOutcome.Down(
        error = Messages.dockerContainerNotFound(
            container,
            dockerHost
        ),
        latencyMs = latencyMs,
    )

    is DockerInspectResult.DaemonError -> DockerCheckOutcome.Down(
        error = message
            ?.takeIf { it.isNotBlank() }
            ?.let { Messages.dockerDaemonErrorWithMessage(statusCode.toString(), it.sanitizeAsError()) }
            ?: Messages.dockerDaemonError(statusCode.toString()),
        latencyMs = latencyMs,
    )

    is DockerInspectResult.Unreachable -> DockerCheckOutcome.Down(
        error = Messages.dockerHostUnreachable(dockerHost, error.sanitizeAsError()),
        latencyMs = null,
    )
}

private fun DockerContainerState.toCheckOutcome(latencyMs: Int): DockerCheckOutcome = when (status) {
    // A healthcheck only runs while the container does, so this is the only status where health can overrule it
    DockerContainerStatus.RUNNING -> if (health == DockerHealthStatus.UNHEALTHY) {
        DockerCheckOutcome.Down(unhealthyReason(), latencyMs)
    } else {
        DockerCheckOutcome.Up(latencyMs)
    }

    DockerContainerStatus.EXITED -> DockerCheckOutcome.Down(exitedReason(), latencyMs)
    DockerContainerStatus.CREATED -> DockerCheckOutcome.Down(Messages.dockerStatusCreated(), latencyMs)
    DockerContainerStatus.PAUSED -> DockerCheckOutcome.Down(Messages.dockerStatusPaused(), latencyMs)
    DockerContainerStatus.RESTARTING -> DockerCheckOutcome.Down(Messages.dockerStatusRestarting(), latencyMs)
    DockerContainerStatus.REMOVING -> DockerCheckOutcome.Down(Messages.dockerStatusRemoving(), latencyMs)
    DockerContainerStatus.DEAD -> DockerCheckOutcome.Down(Messages.dockerStatusDead(), latencyMs)
}

/**
 * The streak is only worth reporting above one: the first failure already flips the daemon's verdict to unhealthy,
 * so "failed 1 time in a row" says nothing the reason does not already.
 */
private fun DockerContainerState.unhealthyReason(): String =
    failingStreak
        ?.takeIf { it > 1 }
        ?.let { Messages.dockerHealthUnhealthyWithStreak(it.toString()) }
        ?: Messages.dockerHealthUnhealthy()

private fun DockerContainerState.exitedReason(): String = when {
    exitCode != null && oomKilled -> Messages.dockerStatusExitedOomKilled(exitCode.toString())
    exitCode != null -> Messages.dockerStatusExited(exitCode.toString())
    oomKilled -> Messages.dockerStatusExitedUnknownCodeOomKilled()
    else -> Messages.dockerStatusExitedUnknownCode()
}
