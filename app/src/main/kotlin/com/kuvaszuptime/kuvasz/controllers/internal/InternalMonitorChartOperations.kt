package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.IcmpChartDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencyChartDto
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import java.time.Duration

/**
 * The data of the metrics charts on the monitor details pages, in the selected period (an ISO-8601 duration). From 7
 * days on, the checks are grouped into buckets, see [com.kuvaszuptime.kuvasz.services.monitor.chartResolutionOf].
 */
interface InternalMonitorChartOperations {

    @Get("/http/{monitorId}")
    fun getHttpMonitorChart(@PathVariable monitorId: Long, @QueryValue period: Duration): LatencyChartDto

    @Get("/tcp/{monitorId}")
    fun getTcpMonitorChart(@PathVariable monitorId: Long, @QueryValue period: Duration): LatencyChartDto

    @Get("/dns/{monitorId}")
    fun getDnsMonitorChart(@PathVariable monitorId: Long, @QueryValue period: Duration): LatencyChartDto

    @Get("/icmp/{monitorId}")
    fun getIcmpMonitorChart(@PathVariable monitorId: Long, @QueryValue period: Duration): IcmpChartDto

    @Get("/docker/{monitorId}")
    fun getDockerMonitorChart(@PathVariable monitorId: Long, @QueryValue period: Duration): DockerChartDto
}
