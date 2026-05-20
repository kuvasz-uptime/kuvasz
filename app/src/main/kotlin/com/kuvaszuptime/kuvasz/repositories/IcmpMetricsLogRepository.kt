package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.percentileCont
import org.jooq.impl.DSL.round
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class IcmpMetricsLogRepository(private val dslContext: DSLContext) {

    companion object {
        private const val P90 = .90
        private const val P95 = .95
        private const val P99 = .99
    }

    fun insertLog(monitorId: Long, latencyMs: Int?, packetLossPercentage: Int) {
        dslContext.insertInto(ICMP_METRICS_LOG)
            .set(
                IcmpMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setPacketLossPercentage(packetLossPercentage)
                    .setCreatedAt(getCurrentTimestamp())
            )
            .execute()
    }

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<IcmpMetricsLogDto> = dslContext
        .metricsLogDtoSelect(monitorId)
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(ICMP_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(ICMP_METRICS_LOG.CREATED_AT.desc(), ICMP_METRICS_LOG.ID.desc())
        .fetchInto(IcmpMetricsLogDto::class.java)

    fun getLatencyMetrics(monitorId: Long, period: Duration): IcmpLatencyMetricResult? {
        val thresholdSeconds = period.toSeconds()
        return dslContext
            .select(
                ICMP_METRICS_LOG.MONITOR_ID.`as`(IcmpLatencyMetricResult::monitorId.name),
                round(avg(ICMP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java).`as`(IcmpLatencyMetricResult::avg.name),
                min(ICMP_METRICS_LOG.LATENCY_MS).`as`(IcmpLatencyMetricResult::min.name),
                max(ICMP_METRICS_LOG.LATENCY_MS).`as`(IcmpLatencyMetricResult::max.name),
                round(percentileCont(P90).withinGroupOrderBy(ICMP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(IcmpLatencyMetricResult::p90.name),
                round(percentileCont(P95).withinGroupOrderBy(ICMP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(IcmpLatencyMetricResult::p95.name),
                round(percentileCont(P99).withinGroupOrderBy(ICMP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(IcmpLatencyMetricResult::p99.name),
            )
            .from(ICMP_METRICS_LOG)
            .where(ICMP_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(ICMP_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            .and(ICMP_METRICS_LOG.LATENCY_MS.isNotNull)
            .groupBy(ICMP_METRICS_LOG.MONITOR_ID)
            .fetchOneInto(IcmpLatencyMetricResult::class.java)
    }

    fun getPacketLossMetrics(monitorId: Long, period: Duration): PacketLossMetricResult? {
        val thresholdSeconds = period.toSeconds()
        return dslContext
            .select(
                ICMP_METRICS_LOG.MONITOR_ID.`as`(PacketLossMetricResult::monitorId.name),
                round(avg(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE)).cast(Int::class.java)
                    .`as`(PacketLossMetricResult::avg.name),
                min(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE).`as`(PacketLossMetricResult::min.name),
                max(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE).`as`(PacketLossMetricResult::max.name),
                round(percentileCont(P90).withinGroupOrderBy(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE))
                    .cast(Int::class.java).`as`(PacketLossMetricResult::p90.name),
                round(percentileCont(P95).withinGroupOrderBy(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE))
                    .cast(Int::class.java).`as`(PacketLossMetricResult::p95.name),
                round(percentileCont(P99).withinGroupOrderBy(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE))
                    .cast(Int::class.java).`as`(PacketLossMetricResult::p99.name),
            )
            .from(ICMP_METRICS_LOG)
            .where(ICMP_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(ICMP_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            .groupBy(ICMP_METRICS_LOG.MONITOR_ID)
            .fetchOneInto(PacketLossMetricResult::class.java)
    }

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(ICMP_METRICS_LOG)
        .where(ICMP_METRICS_LOG.CREATED_AT.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(ICMP_METRICS_LOG)
        .where(ICMP_METRICS_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    private fun DSLContext.metricsLogDtoSelect(monitorId: Long) =
        select(
            ICMP_METRICS_LOG.ID.`as`(IcmpMetricsLogDto::id.name),
            ICMP_METRICS_LOG.LATENCY_MS.`as`(IcmpMetricsLogDto::latencyInMs.name),
            ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE.`as`(IcmpMetricsLogDto::packetLossPercentage.name),
            ICMP_METRICS_LOG.CREATED_AT.`as`(IcmpMetricsLogDto::createdAt.name),
        )
            .from(ICMP_METRICS_LOG)
            .where(ICMP_METRICS_LOG.MONITOR_ID.eq(monitorId))

    fun fetchLastByMonitorId(monitorId: Long): IcmpMetricsLogDto? = dslContext
        .metricsLogDtoSelect(monitorId)
        .orderBy(ICMP_METRICS_LOG.CREATED_AT.desc(), ICMP_METRICS_LOG.ID.desc())
        .limit(1)
        .fetchOneInto(IcmpMetricsLogDto::class.java)
}

@Introspected
data class IcmpLatencyMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val min: Int?,
    val max: Int?,
    val p90: Int?,
    val p95: Int?,
    val p99: Int?,
)

@Introspected
data class PacketLossMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val min: Int?,
    val max: Int?,
    val p90: Int?,
    val p95: Int?,
    val p99: Int?,
)
