package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.toDurationString
import java.math.BigDecimal
import java.time.OffsetDateTime

sealed class DockerUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: DockerUptimeEventRecord?
    abstract val image: String?
    abstract val restartCount: Int?
    abstract val containerCreatedAt: OffsetDateTime?
}

/**
 * [latencyInMs] is the round-trip to the Docker daemon, not anything about the container, so unlike the other types
 * it is deliberately kept out of the notification. It rides along only for the API latency exporter, which is driven
 * by these events; the metrics history takes it straight from the check.
 *
 * [cpuUsagePercent] and [memoryUsageBytes] are the container's own resource sample, present only when the monitor
 * keeps a metrics history and the container was running. They ride along for their exporters too, since sampling
 * twice would cost another second of the daemon's collection cycle.
 *
 * [image] is the image the container was created from, [restartCount] is how many times its restart policy restarted
 * it, and [containerCreatedAt] tells one container apart from a recreated one. All of them are unknown when the
 * container could not be inspected.
 */
data class DockerMonitorUpEvent(
    override val monitor: DockerMonitorRecord,
    override val previousEvent: DockerUptimeEventRecord?,
    val latencyInMs: Int?,
    val cpuUsagePercent: BigDecimal? = null,
    val memoryUsageBytes: Long? = null,
    override val image: String? = null,
    override val restartCount: Int? = null,
    override val containerCreatedAt: OffsetDateTime? = null,
) : DockerUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() = StructuredDockerMonitorUpMessage(
        summary = Messages.yourDockerMonitorIsUp(monitor.name),
        previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) },
    )
}

data class DockerMonitorDownEvent(
    override val monitor: DockerMonitorRecord,
    val error: String,
    override val previousEvent: DockerUptimeEventRecord?,
    val latencyInMs: Int? = null,
    override val image: String? = null,
    override val restartCount: Int? = null,
    override val containerCreatedAt: OffsetDateTime? = null,
) : DockerUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage() = StructuredDockerMonitorDownMessage(
        summary = Messages.yourDockerMonitorIsDown(monitor.name),
        error = Messages.reasonExplanation(error),
        previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) },
    )
}
