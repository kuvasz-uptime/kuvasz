package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.MonitorWithMetricsHistory
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.ChartIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencyChartDto
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.periodStart
import com.kuvaszuptime.kuvasz.repositories.toStatsDto
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorTypeSupport
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import java.time.Duration
import java.time.OffsetDateTime

// Far beyond any history, it only keeps the arithmetic of the dates from overflowing
private const val MAX_CHART_SPAN_DAYS = 36_500L
private val MAX_CHART_SPAN: Duration = Duration.ofDays(MAX_CHART_SPAN_DAYS)

/**
 * The data of the metrics charts on the monitor details pages, either of a `period` until now (e.g. the last 24 hours),
 * or between `from` and `to` (both inclusive). The checks are grouped into buckets, see [chartBucketWidthOf].
 */
@Singleton
class MonitorChartService(
    private val http: HttpMonitorTypeSupport,
    private val tcp: TcpMonitorTypeSupport,
    private val dns: DnsMonitorTypeSupport,
    private val icmp: IcmpMonitorTypeSupport,
    private val docker: DockerMonitorTypeSupport,
    private val incidentRepository: IncidentRepository,
) {

    fun getHttpMonitorChart(monitorId: Long, period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?) =
        latencyChart(http, IncidentType.HTTP, monitorId, chartPeriodOf(period, from, to))

    fun getTcpMonitorChart(monitorId: Long, period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?) =
        latencyChart(tcp, IncidentType.TCP, monitorId, chartPeriodOf(period, from, to))

    fun getDnsMonitorChart(monitorId: Long, period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?) =
        latencyChart(dns, IncidentType.DNS, monitorId, chartPeriodOf(period, from, to))

    fun getIcmpMonitorChart(monitorId: Long, period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?) =
        icmpChart(monitorId, chartPeriodOf(period, from, to))

    fun getDockerMonitorChart(monitorId: Long, period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?) =
        dockerChart(monitorId, chartPeriodOf(period, from, to))

    private fun icmpChart(monitorId: Long, chartPeriod: ChartPeriod): IcmpChartDto {
        val (from, to, bucketWidth) = chartPeriod
        return if (!hasMetricsHistory(icmp, monitorId)) {
            IcmpChartDto.empty()
        } else {
            with(icmp.metricsLogRepository) {
                IcmpChartDto(
                    latencyStats = getLatencyMetricsBetween(monitorId, from, to)?.toStatsDto(),
                    packetLossStats = getPacketLossMetricsBetween(monitorId, from, to)?.toStatsDto(),
                    series = fetchIcmpSeries(monitorId, from, to, bucketWidth),
                    incidents = incidentsOf(IncidentType.ICMP, monitorId, from, to),
                )
            }
        }
    }

    private fun dockerChart(monitorId: Long, chartPeriod: ChartPeriod): DockerChartDto {
        val (from, to, bucketWidth) = chartPeriod
        return if (!hasMetricsHistory(docker, monitorId)) {
            DockerChartDto.empty()
        } else {
            with(docker.metricsLogRepository) {
                DockerChartDto(
                    cpuStats = getCpuUsageMetricsBetween(monitorId, from, to)?.toStatsDto(),
                    memoryStats = getMemoryUsageMetricsBetween(monitorId, from, to)?.toStatsDto(),
                    series = fetchDockerSeries(monitorId, from, to, bucketWidth),
                    restarts = fetchRestarts(monitorId, from, to),
                    incidents = incidentsOf(IncidentType.DOCKER, monitorId, from, to),
                )
            }
        }
    }

    private fun <R> latencyChart(
        typeSupport: MetricsHistoryMonitorTypeSupport<*, R, *>,
        incidentType: IncidentType,
        monitorId: Long,
        chartPeriod: ChartPeriod,
    ): LatencyChartDto where R : MonitorRecord, R : MonitorWithMetricsHistory {
        val (from, to, bucketWidth) = chartPeriod
        return if (!hasMetricsHistory(typeSupport, monitorId)) {
            LatencyChartDto.empty()
        } else {
            with(typeSupport.metricsLogRepository) {
                LatencyChartDto(
                    latencyStats = getLatencyMetricsBetween(monitorId, from, to)?.toStatsDto(),
                    series = fetchLatencySeries(monitorId, from, to, bucketWidth),
                    incidents = incidentsOf(incidentType, monitorId, from, to),
                )
            }
        }
    }

    /** Throws if there is no monitor with the given ID. */
    private fun <R> hasMetricsHistory(typeSupport: MetricsHistoryMonitorTypeSupport<*, R, *>, monitorId: Long): Boolean
        where R : MonitorRecord, R : MonitorWithMetricsHistory =
        typeSupport.repository.findById(monitorId, null)
            ?.metricsHistoryEnabled
            ?: throw MonitorNotFoundException(monitorId)

    /** A period until now ([to] is null), or between two moments, and the length of the buckets its checks go into. */
    private data class ChartPeriod(val from: OffsetDateTime, val to: OffsetDateTime?, val bucketWidth: Duration)

    private fun chartPeriodOf(period: Duration?, from: OffsetDateTime?, to: OffsetDateTime?): ChartPeriod = when {
        period != null && from == null && to == null && period > Duration.ZERO && period <= MAX_CHART_SPAN ->
            ChartPeriod(periodStart(period), null, chartBucketWidthOf(period))

        period == null && from != null && to != null && from.isBefore(to) &&
            Duration.between(from, to) <= MAX_CHART_SPAN ->
            ChartPeriod(from, to, chartBucketWidthOf(Duration.between(from, to)))

        else -> throw ValidationException(
            "Either a positive 'period', or a 'from' earlier than 'to' has to be set, " +
                "not longer than $MAX_CHART_SPAN"
        )
    }

    private fun incidentsOf(type: IncidentType, monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?) =
        incidentRepository.getIncidentsOfMonitorBetween(type, monitorId, from, to).map { incident ->
            ChartIncidentDto(
                startedAt = incident.startedAt.toInstant().toEpochMilli(),
                endedAt = incident.endedAt?.toInstant()?.toEpochMilli(),
                details = incident.details,
            )
        }
}
