package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpSeriesDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class IcmpMetricsLogRepository(dslContext: DSLContext) :
    MonitorMetricsLogRepository<IcmpMetricsLogRecord, IcmpMetricsLogDto>(
        dslContext,
        MetricsLogTable(
            table = ICMP_METRICS_LOG,
            id = ICMP_METRICS_LOG.ID,
            monitorId = ICMP_METRICS_LOG.MONITOR_ID,
            createdAt = ICMP_METRICS_LOG.CREATED_AT,
            latency = ICMP_METRICS_LOG.LATENCY_MS,
        ),
        IcmpMetricsLogDto::class.java,
    ) {

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

    fun getPacketLossMetrics(monitorId: Long, period: Duration): PacketLossMetricResult? =
        getPacketLossMetricsBetween(monitorId, from = periodStart(period), to = null)

    fun getPacketLossMetricsBetween(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?) = aggregate(
        ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE,
        monitorId,
        from,
        to,
        PacketLossMetricResult::class.java,
        withPercentiles = true,
    )

    fun getAveragePacketLosses(monitorIds: Collection<Long>, period: Duration): Map<Long, Int> =
        averagesByMonitor(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE, monitorIds, period)

    fun fetchIcmpSeries(
        monitorId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        width: Duration,
    ): IcmpSeriesDto {
        val latency = chartQueries.measurement(ICMP_METRICS_LOG.LATENCY_MS, "latency")
        val packetLoss = chartQueries.measurement(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE, "packet_loss")
        val withoutReading = chartQueries.checksWithoutReading(ICMP_METRICS_LOG.LATENCY_MS)
        val buckets = chartQueries.fetchBuckets(
            monitorId = monitorId,
            from = from,
            to = to,
            width = width,
            selected = listOf(
                withoutReading,
                latency.average,
                latency.lowest,
                latency.highest,
                packetLoss.average,
                packetLoss.lowest,
                packetLoss.highest,
            ),
        )
        return IcmpSeriesDto(
            timestamps = buckets.getValues(chartQueries.bucketTimestamp),
            checks = buckets.getValues(chartQueries.checks),
            checksWithoutReading = buckets.getValues(withoutReading),
            latencies = buckets.getValues(latency.average),
            lowestLatencies = buckets.getValues(latency.lowest),
            highestLatencies = buckets.getValues(latency.highest),
            packetLossPercentages = buckets.getValues(packetLoss.average),
            lowestPacketLossPercentages = buckets.getValues(packetLoss.lowest),
            highestPacketLossPercentages = buckets.getValues(packetLoss.highest),
        )
    }

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            ICMP_METRICS_LOG.ID.`as`(IcmpMetricsLogDto::id.name),
            ICMP_METRICS_LOG.LATENCY_MS.`as`(IcmpMetricsLogDto::latencyInMs.name),
            ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE.`as`(IcmpMetricsLogDto::packetLossPercentage.name),
            ICMP_METRICS_LOG.CREATED_AT.`as`(IcmpMetricsLogDto::createdAt.name),
        )
            .from(ICMP_METRICS_LOG)
            .where(ICMP_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
