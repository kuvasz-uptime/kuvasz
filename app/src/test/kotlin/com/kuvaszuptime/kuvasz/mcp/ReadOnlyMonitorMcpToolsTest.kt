package com.kuvaszuptime.kuvasz.mcp

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest(environments = ["yaml-monitors-empty-array"])
class HttpReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
) : McpToolTest(client) {

    init {
        given("the HTTP monitor MCP tools when monitors are configured via YAML") {

            `when`("create-http-monitor is called") {
                val response = callTool(
                    "create-http-monitor",
                    mapOf(
                        "name" to "readonly-test-monitor",
                        "url" to "https://example.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("toggle-http-monitor is called") {
                val response = callTool(
                    "toggle-http-monitor",
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-icmp-monitors-empty-array"])
class IcmpReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
) : McpToolTest(client) {

    init {
        given("the ICMP monitor MCP tools when monitors are configured via YAML") {

            `when`("create-icmp-monitor is called") {
                val response = callTool(
                    "create-icmp-monitor",
                    mapOf(
                        "name" to "readonly-test-icmp-monitor",
                        "host" to "10.0.0.1",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("toggle-icmp-monitor is called") {
                val response = callTool(
                    "toggle-icmp-monitor",
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-push-monitors-empty-array"])
class PushReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
) : McpToolTest(client) {

    init {
        given("the push monitor MCP tools when monitors are configured via YAML") {

            `when`("create-push-monitor is called") {
                val response = callTool(
                    "create-push-monitor",
                    mapOf(
                        "name" to "readonly-test-push-monitor",
                        "heartbeatInterval" to 300,
                        "clientSecret" to "test-secret-abc123",
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("toggle-push-monitor is called") {
                val response = callTool(
                    "toggle-push-monitor",
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }
        }
    }
}
