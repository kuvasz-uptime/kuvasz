package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.MonitorWithMetricsHistory
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencyChartDto
import com.kuvaszuptime.kuvasz.repositories.toStatsDto
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorTypeSupport
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class MonitorChartService(
    private val http: HttpMonitorTypeSupport,
    private val tcp: TcpMonitorTypeSupport,
    private val dns: DnsMonitorTypeSupport,
    private val icmp: IcmpMonitorTypeSupport,
    private val docker: DockerMonitorTypeSupport,
) {

    fun getHttpMonitorChart(monitorId: Long, period: Duration): LatencyChartDto = latencyChart(http, monitorId, period)

    fun getTcpMonitorChart(monitorId: Long, period: Duration): LatencyChartDto = latencyChart(tcp, monitorId, period)

    fun getDnsMonitorChart(monitorId: Long, period: Duration): LatencyChartDto = latencyChart(dns, monitorId, period)

    fun getIcmpMonitorChart(monitorId: Long, period: Duration) =
        if (!hasMetricsHistory(icmp, monitorId)) {
            IcmpChartDto(latencyStats = null, packetLossStats = null, points = emptyList())
        } else {
            with(icmp.metricsLogRepository) {
                IcmpChartDto(
                    latencyStats = getLatencyMetrics(monitorId, period)?.toStatsDto(),
                    packetLossStats = getPacketLossMetrics(monitorId, period)?.toStatsDto(),
                    points = fetchIcmpChartPoints(monitorId, period, chartResolutionOf(period)),
                )
            }
        }

    fun getDockerMonitorChart(monitorId: Long, period: Duration) =
        if (!hasMetricsHistory(docker, monitorId)) {
            DockerChartDto(cpuStats = null, memoryStats = null, points = emptyList(), restarts = emptyList())
        } else {
            with(docker.metricsLogRepository) {
                DockerChartDto(
                    cpuStats = getCpuUsageMetrics(monitorId, period)?.toStatsDto(),
                    memoryStats = getMemoryUsageMetrics(monitorId, period)?.toStatsDto(),
                    points = fetchDockerChartPoints(monitorId, period, chartResolutionOf(period)),
                    restarts = fetchRestartPoints(monitorId, period),
                )
            }
        }

    private fun <R> latencyChart(
        typeSupport: MetricsHistoryMonitorTypeSupport<*, R, *>,
        monitorId: Long,
        period: Duration,
    ) where R : MonitorRecord, R : MonitorWithMetricsHistory =
        if (!hasMetricsHistory(typeSupport, monitorId)) {
            LatencyChartDto(latencyStats = null, points = emptyList())
        } else {
            with(typeSupport.metricsLogRepository) {
                LatencyChartDto(
                    latencyStats = getLatencyMetrics(monitorId, period)?.toStatsDto(),
                    points = fetchLatencyChartPoints(monitorId, period, chartResolutionOf(period)),
                )
            }
        }

    private fun <R> hasMetricsHistory(typeSupport: MetricsHistoryMonitorTypeSupport<*, R, *>, monitorId: Long): Boolean
        where R : MonitorRecord, R : MonitorWithMetricsHistory =
        typeSupport.repository.findById(monitorId, null)
            ?.metricsHistoryEnabled
            ?: throw MonitorNotFoundException(monitorId)
}
