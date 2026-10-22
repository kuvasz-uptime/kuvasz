package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartPointDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.Duration

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
        aggregateWithPercentiles(
            ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE,
            monitorId,
            period,
            PacketLossMetricResult::class.java,
        )

    fun getAveragePacketLosses(monitorIds: Collection<Long>, period: Duration): Map<Long, Int> =
        averagesByMonitor(ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE, monitorIds, period)

    fun fetchIcmpChartPoints(monitorId: Long, period: Duration, resolution: Duration?): List<IcmpChartPointDto> =
        chartQueries.fetchPoints(
            monitorId = monitorId,
            period = period,
            resolution = resolution,
            createdAtAlias = IcmpChartPointDto::createdAt.name,
            measurements = listOf(
                chartQueries.measurement(
                    ICMP_METRICS_LOG.LATENCY_MS,
                    IcmpChartPointDto::latencyInMs.name,
                    IcmpChartPointDto::maxLatencyInMs.name,
                ),
                chartQueries.measurement(
                    ICMP_METRICS_LOG.PACKET_LOSS_PERCENTAGE,
                    IcmpChartPointDto::packetLossPercentage.name,
                    IcmpChartPointDto::maxPacketLossPercentage.name,
                ),
            ),
            pointType = IcmpChartPointDto::class.java,
        )

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
