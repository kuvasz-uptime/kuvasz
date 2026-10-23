package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencyChartDto
import com.kuvaszuptime.kuvasz.services.monitor.MonitorChartService
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden
import java.time.Duration
import java.time.OffsetDateTime

@Controller("$API_INTERNAL_PREFIX/monitor-charts")
@Hidden
class InternalMonitorChartController(
    private val monitorChartService: MonitorChartService,
) : InternalMonitorChartOperations {

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getHttpMonitorChart(
        monitorId: Long,
        period: Duration?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): LatencyChartDto = monitorChartService.getHttpMonitorChart(monitorId, period, from, to)

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getTcpMonitorChart(
        monitorId: Long,
        period: Duration?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): LatencyChartDto = monitorChartService.getTcpMonitorChart(monitorId, period, from, to)

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getDnsMonitorChart(
        monitorId: Long,
        period: Duration?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): LatencyChartDto = monitorChartService.getDnsMonitorChart(monitorId, period, from, to)

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getIcmpMonitorChart(
        monitorId: Long,
        period: Duration?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): IcmpChartDto = monitorChartService.getIcmpMonitorChart(monitorId, period, from, to)

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getDockerMonitorChart(
        monitorId: Long,
        period: Duration?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): DockerChartDto = monitorChartService.getDockerMonitorChart(monitorId, period, from, to)
}
