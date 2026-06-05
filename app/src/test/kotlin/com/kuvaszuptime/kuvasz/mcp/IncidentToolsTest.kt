package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class IncidentToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client, objectMapper) {

    init {
        given("the Incident tools") {

            `when`("list-incidents is called with an ongoing incident in the DB") {
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                    error = "Connection refused",
                )
                val response = callTool("list-incidents")

                then("it should return the incident in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val incidents = objectMapper.convertValue<List<IncidentDto>>(
                        result.structuredContent.shouldNotBeNull()["incidents"]
                    )
                    incidents.forOne { it.monitorName shouldBe monitor.name }

                    objectMapper.convertValue<List<IncidentDto>>(
                        objectMapper.readTree(result.firstText())["incidents"]
                    ) shouldBe incidents
                }
            }

            `when`("list-incidents is called with an ISO 8601 period string") {
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                )
                val response = callTool("list-incidents", mapOf("period" to "PT2H"))

                then("it should resolve the duration and return matching incidents") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val incidents = objectMapper.convertValue<List<IncidentDto>>(
                        result.structuredContent.shouldNotBeNull()["incidents"]
                    )
                    incidents.forOne { it.monitorName shouldBe monitor.name }

                    objectMapper.convertValue<List<IncidentDto>>(
                        objectMapper.readTree(result.firstText())["incidents"]
                    ) shouldBe incidents
                }
            }

            `when`("list-incidents is filtered by monitorId with no incidents") {
                val monitor = createHttpMonitor(monitorRepository)
                val response = callTool("list-incidents", mapOf("monitorId" to monitor.id, "includeResolved" to false))

                then("it should return an empty incidents list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    objectMapper.convertValue<List<IncidentDto>>(
                        result.structuredContent.shouldNotBeNull()["incidents"]
                    ).shouldBeEmpty()

                    objectMapper.convertValue<List<IncidentDto>>(
                        objectMapper.readTree(result.firstText())["incidents"]
                    ).shouldBeEmpty()
                }
            }
        }
    }
}
