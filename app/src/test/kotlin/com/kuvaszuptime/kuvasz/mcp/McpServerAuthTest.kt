package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import com.kuvaszuptime.kuvasz.security.TEST_API_KEY
import com.kuvaszuptime.kuvasz.security.TEST_PASSWORD
import com.kuvaszuptime.kuvasz.security.TEST_USERNAME
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
class McpServerAuthTest(
    @param:Client("/") private val client: HttpClient,
    private val objectMapper: ObjectMapper,
) : DatabaseBehaviorSpec() {

    init {
        given("the MCP endpoint") {

            `when`("an unauthenticated request is made") {
                val request = HttpRequest.POST(MCP_PATH, jsonRpcRequest("tools/list"))
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a request is made with a wrong API key") {
                val request = HttpRequest.POST(MCP_PATH, jsonRpcRequest("tools/list"))
                    .header("X-API-KEY", "wrongkey1234567890")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a valid tools/list request is made") {
                val response = listTools()

                then("it should return all registered MCP tools") {
                    val toolNames = response.result.shouldNotBeNull().tools.map { it.name }
                    toolNames.shouldContainAll(
                        "list-http-monitors",
                        "get-http-monitor-details",
                        "create-http-monitor",
                        "update-http-monitor",
                        "list-icmp-monitors",
                        "get-icmp-monitor-details",
                        "update-icmp-monitor",
                        "list-push-monitors",
                        "get-push-monitor-details",
                        "update-push-monitor",
                        "list-incidents",
                        "get-http-monitor-stats",
                        "get-icmp-monitor-stats",
                        "get-push-monitor-stats",
                    )
                }
            }
        }
    }

    private suspend fun listTools(): McpToolsListResponse {
        val request = HttpRequest.POST(MCP_PATH, jsonRpcRequest("tools/list"))
            .header("X-API-KEY", TEST_API_KEY)
        val body = client.exchange(request, String::class.java).awaitFirst().body().shouldNotBeNull()
        return objectMapper.readValue(body)
    }
}
