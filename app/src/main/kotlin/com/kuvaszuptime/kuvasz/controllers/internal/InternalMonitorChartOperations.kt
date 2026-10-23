package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencyChartDto
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import java.time.Duration
import java.time.OffsetDateTime

/**
 * The data of the metrics charts on the monitor details pages, see
 * [com.kuvaszuptime.kuvasz.services.monitor.MonitorChartService].
 */
interface InternalMonitorChartOperations {

    @Get("/http/{monitorId}")
    fun getHttpMonitorChart(
        @PathVariable monitorId: Long,
        @QueryValue period: Duration?,
        @QueryValue from: OffsetDateTime?,
        @QueryValue to: OffsetDateTime?,
    ): LatencyChartDto

    @Get("/tcp/{monitorId}")
    fun getTcpMonitorChart(
        @PathVariable monitorId: Long,
        @QueryValue period: Duration?,
        @QueryValue from: OffsetDateTime?,
        @QueryValue to: OffsetDateTime?,
    ): LatencyChartDto

    @Get("/dns/{monitorId}")
    fun getDnsMonitorChart(
        @PathVariable monitorId: Long,
        @QueryValue period: Duration?,
        @QueryValue from: OffsetDateTime?,
        @QueryValue to: OffsetDateTime?,
    ): LatencyChartDto

    @Get("/icmp/{monitorId}")
    fun getIcmpMonitorChart(
        @PathVariable monitorId: Long,
        @QueryValue period: Duration?,
        @QueryValue from: OffsetDateTime?,
        @QueryValue to: OffsetDateTime?,
    ): IcmpChartDto

    @Get("/docker/{monitorId}")
    fun getDockerMonitorChart(
        @PathVariable monitorId: Long,
        @QueryValue period: Duration?,
        @QueryValue from: OffsetDateTime?,
        @QueryValue to: OffsetDateTime?,
    ): DockerChartDto
}
