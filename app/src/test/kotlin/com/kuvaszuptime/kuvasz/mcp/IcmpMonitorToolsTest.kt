package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorStatsDto
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import io.kotest.inspectors.forOne
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.readValue

@MicronautTest
class IcmpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client, objectMapper) {

    init {
        given("the MCP endpoint") {

            `when`("list-icmp-monitors is called with monitors in the DB") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("list-icmp-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<IcmpMonitorDetailsDto>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<IcmpMonitorDetailsDto>>(
                        objectMapper.readTree(result.firstText())["monitors"]
                    ) shouldBe monitors
                }
            }

            `when`("get-icmp-monitor-details is called with a valid ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("get-icmp-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val details = objectMapper.convertValue<IcmpMonitorDetailsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<IcmpMonitorDetailsDto>(result.firstText()) shouldBe details
                }
            }

            `when`("get-icmp-monitor-details is called with a non-existent ID") {
                val response = callTool("get-icmp-monitor-details", mapOf("monitorId" to -999L))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.firstText() shouldContain "-999"
                    result.structuredContent.shouldBeNull()
                }
            }

            `when`("get-icmp-monitor-stats is called with a valid monitor ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("get-icmp-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<IcmpMonitorStatsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id
                    stats.metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                    objectMapper.readValue<IcmpMonitorStatsDto>(result.firstText()) shouldBe stats
                }
            }

            `when`("update-icmp-monitor is called with a valid partial patch") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val response = callTool(
                    "update-icmp-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<IcmpMonitorDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false

                    objectMapper.readValue<IcmpMonitorDto>(result.firstText()) shouldBe updated
                }
            }

            `when`("update-icmp-monitor omits a field") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, uptimeCheckInterval = 120)
                val response = callTool(
                    "update-icmp-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("the omitted field should keep its current value") {
                    val updated = objectMapper.convertValue<IcmpMonitorDto>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.uptimeCheckInterval shouldBe 120
                }
            }
        }
    }
}
