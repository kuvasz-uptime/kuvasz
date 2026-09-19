package com.kuvaszuptime.kuvasz.services.docker.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Turns a container's stats payload into the two figures `docker stats` itself shows, following the same cli
 * routines it computes them with.
 *
 * Only the unix ones. The cli keeps a parallel set for Windows containers - CPU from the wall clock between the two
 * reads, memory straight from `PrivateWorkingSet` - which is out of scope here: a Windows container has no cgroup,
 * so the version heuristic finds nothing to read and the sample is reported unavailable rather than guessed at.
 *
 * The formulas are the pinned v1.40 spec's own, with one deliberate departure on the memory side. The spec
 * subtracts `stats.cache` on a cgroup v1 host, and that was correct for the engine it describes: the docker cli
 * computed it that way "on Docker 19.03 and older", which is exactly the engine that shipped API v1.40. It has since
 * changed, and does not vary by API version, because it lives in the cli rather than in the daemon's response.
 *
 * `calculateMemUsageUnixNoCache` in docker/cli `cli/command/container/stats_helpers.go` now subtracts
 * `total_inactive_file` on cgroup v1 and `inactive_file` on cgroup v2, a definition its own comment calls
 * "consistent with cadvisor and containerd/CRI" (background: moby/moby#40727, "Docker stats memory doesn't include
 * tmpfs usage"). Following the cli is what keeps these numbers equal to `docker stats`, which is what the spec's
 * formulas set out to reproduce in the first place - verified live against Engine 29.8.0, where this agreed with
 * `docker stats` to the byte.
 */
internal object DockerStatsParser {

    private const val PERCENT = 100.0

    fun parse(objectMapper: ObjectMapper, body: String): DockerContainerStats? {
        val payload = runCatching { objectMapper.readValue<StatsResponse>(body) }.getOrNull()
        val memory = payload?.memory
        val cgroupVersion = memory?.cgroupVersion()

        return cgroupVersion?.let { version ->
            DockerContainerStats(
                cgroupVersion = version,
                cpuUsagePercent = payload.cpuUsagePercent(),
                memoryUsageBytes = memory.usedBytes(version),
                // `calculateMemPercentUnixNoCache` guards the same 0, on the grounds that the limit "will never be
                // 0 unless the container is not running and we haven't got any data from cgroup". An unconstrained
                // container reports the host's total memory instead, so a 0 is a missing counter, not a real cap.
                memoryLimitBytes = memory.limit?.takeIf { limit -> limit > 0 },
            )
        }
    }

    /**
     * Decided by which key holds the page cache, because that is the only difference the sampling cares about. The
     * `total_`-prefixed key has to be looked for first: a v1 host reports both forms, a v2 host only the plain one.
     */
    private fun MemoryNode.cgroupVersion(): DockerCgroupVersion? =
        stats?.keys?.let { keys -> DockerCgroupVersion.entries.firstOrNull { it.inactiveFileKey in keys } }

    private fun MemoryNode.usedBytes(cgroupVersion: DockerCgroupVersion): Long? =
        usage?.let { total ->
            // The cli guards each branch with `v < mem.Usage` and otherwise falls through to the raw usage: a page
            // cache larger than the usage it is part of is nonsense, and beats reporting an underflow
            total - (stats?.get(cgroupVersion.inactiveFileKey)?.takeIf { it < total } ?: 0L)
        }

    /**
     * `calculateCPUPercentUnix` from the same cli file, down to the order the multiplications happen in.
     *
     * It needs both reads, which is what makes the non-streaming call worth its wait: one response carries the
     * previous sample in `precpu_stats`, so no state has to be kept between checks.
     *
     * The one deliberate difference is the null: the cli starts at `0.0` and only overwrites it when the guard
     * passes, so a payload carrying no CPU counters at all prints as an idle container. That is harmless for a
     * number on a terminal and wrong for a stored series, where a reader cannot tell a container that used nothing
     * from one that was never measured - a Windows container, whose CPU the cli computes from wall clock instead,
     * being the real case. Unmeasured stays null here; idle stays 0.0, exactly as the cli has it.
     */
    private fun StatsResponse.cpuUsagePercent(): Double? {
        val cpuDelta = delta(cpu?.usage?.total, preCpu?.usage?.total)
        val systemDelta = delta(cpu?.systemUsage, preCpu?.systemUsage)
        // v1.40 names the two as each other's fallback ("if either `precpu_stats.online_cpus` or
        // `cpu_stats.online_cpus` is nil then ... the length of the corresponding `cpu_usage.percpu_usage` array
        // should be used"), for old daemons. A cgroup v2 host needs it the other way round, reporting no
        // `percpu_usage` at all - which only later spec versions spell out. A reported 0 has to take the fallback
        // too: Go decodes an absent `online_cpus` to 0, so the cli's `onlineCPUs == 0.0` covers both cases at once.
        val cpus = cpu?.onlineCpus?.takeIf { it > 0 } ?: cpu?.usage?.perCpu?.size

        return when {
            cpuDelta == null || systemDelta == null || cpus == null -> null
            // A container that burned no CPU between the two reads is idle, which is a measurement, not a gap. The
            // cli lands on the same 0.0 here, either by failing its `systemDelta > 0 && cpuDelta > 0` guard or by
            // multiplying through a zero CPU count.
            cpuDelta <= 0 || systemDelta <= 0 || cpus <= 0 -> 0.0
            else -> cpuDelta.toDouble() / systemDelta * cpus * PERCENT
        }
    }

    /**
     * Subtracted as integers, unlike the cli, which widens each counter to a float first. The counters are
     * nanosecond totals that outgrow a double's exact range after a few months of uptime, and the difference between
     * two of them always fits a Long, so this is the same arithmetic carried out without the rounding.
     */
    private fun delta(current: Long?, previous: Long?): Long? =
        if (current != null && previous != null) current - previous else null

    private data class StatsResponse(
        @param:JsonProperty("cpu_stats")
        val cpu: CpuNode?,
        @param:JsonProperty("precpu_stats")
        val preCpu: CpuNode?,
        @param:JsonProperty("memory_stats")
        val memory: MemoryNode?,
    )

    private data class CpuNode(
        @param:JsonProperty("cpu_usage")
        val usage: CpuUsageNode?,
        @param:JsonProperty("system_cpu_usage")
        val systemUsage: Long?,
        @param:JsonProperty("online_cpus")
        val onlineCpus: Int?,
    )

    private data class CpuUsageNode(
        @param:JsonProperty("total_usage")
        val total: Long?,
        @param:JsonProperty("percpu_usage")
        val perCpu: List<Long>?,
    )

    private data class MemoryNode(
        @param:JsonProperty("usage")
        val usage: Long?,
        @param:JsonProperty("limit")
        val limit: Long?,
        @param:JsonProperty("stats")
        val stats: Map<String, Long>?,
    )
}
