package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
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
class PushMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val pushMonitorRepository: PushMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client, objectMapper) {

    init {
        given("the PUSH monitor tools") {

            `when`("list-push-monitors is called with monitors in the DB") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("list-push-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<PushMonitorDetailsDto>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<PushMonitorDetailsDto>>(
                        objectMapper.readTree(result.firstText())["monitors"]
                    ) shouldBe monitors
                }
            }

            `when`("get-push-monitor-details is called with a valid ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("get-push-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val details = objectMapper.convertValue<PushMonitorDetailsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<PushMonitorDetailsDto>(result.firstText()) shouldBe details
                }
            }

            `when`("get-push-monitor-details is called with a non-existent ID") {
                val response = callTool("get-push-monitor-details", mapOf("monitorId" to -999L))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.firstText() shouldContain "-999"
                    result.structuredContent.shouldBeNull()
                }
            }

            `when`("get-push-monitor-stats is called with a valid monitor ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("get-push-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<PushMonitorStatsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id

                    objectMapper.readValue<PushMonitorStatsDto>(result.firstText()) shouldBe stats
                }
            }

            `when`("update-push-monitor is called with a valid partial patch") {
                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val response = callTool(
                    "update-push-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<PushMonitorDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false

                    objectMapper.readValue<PushMonitorDto>(result.firstText()) shouldBe updated
                }
            }

            `when`("update-push-monitor omits a field") {
                val monitor = createPushMonitor(pushMonitorRepository, heartbeatInterval = 600)
                val response = callTool(
                    "update-push-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("the omitted field should keep its current value") {
                    val updated = objectMapper.convertValue<PushMonitorDto>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.heartbeatInterval shouldBe 600
                }
            }
        }
    }
}
