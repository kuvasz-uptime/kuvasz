package com.kuvaszuptime.kuvasz.services.docker

/**
 * Which cgroup hierarchy the container's host runs, together with the key that holds its page cache.
 *
 * It is never asked for: `SystemInfo.CgroupVersion` only exists from API v1.41 and an Engine 19.03 daemon is
 * spoken to on v1.40, so the stats payload itself has to settle it. That is no loss, because the only thing the
 * version changes for a resource sample is which key holds the page cache that comes off the raw memory usage - and
 * the payload answers exactly that question. A v1 host reports both its own counters and the `total_`-prefixed sums
 * over the subtree, a v2 host only ever reports the unprefixed keys, so the prefixed one is what tells the two apart,
 * and it has to be looked for first.
 */
enum class DockerCgroupVersion(val inactiveFileKey: String) {
    V1("total_inactive_file"),
    V2("inactive_file"),
}

/**
 * A single resource sample of a container, in the terms `docker stats` itself reports.
 *
 * The counters stay nullable because the Engine API leaves out what a given host cannot account for, and a sample
 * missing one of them is still worth recording. [cgroupVersion] is not nullable: a payload it cannot be read from
 * carries no live cgroup at all - a container that stopped between the inspection and this call - which is reported
 * as an unavailable sample instead.
 */
data class DockerContainerStats(
    val cgroupVersion: DockerCgroupVersion,
    val cpuUsagePercent: Double?,
    val memoryUsageBytes: Long?,
    val memoryLimitBytes: Long?,
)

/**
 * The outcome of a single resource sampling.
 *
 * Unlike [DockerInspectResult] this has no per-failure variants, because sampling is best effort and never decides
 * whether a monitor is up: the inspection does that. Every way it can fail collapses into one branch the caller can
 * only log, carrying the reason for that log line.
 */
sealed interface DockerStatsResult {

    data class Measured(val stats: DockerContainerStats) : DockerStatsResult

    data class Unavailable(val reason: String) : DockerStatsResult
}
