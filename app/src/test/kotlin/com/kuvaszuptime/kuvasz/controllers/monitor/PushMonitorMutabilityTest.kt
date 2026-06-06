package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.mocks.randomClientSecret
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.node.JsonNodeFactory

@MicronautTest(environments = ["yaml-push-monitors", "full-integrations-setup"])
class PushMonitorMutabilityTest(
    @param:Client("/") private val client: HttpClient,
) : DatabaseStringSpec({

    val monitorCreateDto = PushMonitorCreateDto(
        name = "something",
        heartbeatInterval = 5149,
        gracePeriod = 1,
        clientSecret = randomClientSecret(),
    )
    val monitorUpdateDto = JsonNodeFactory.instance.objectNode().put(PushMonitorUpdateDto::enabled.name, false)

    "all the API endpoints that mutate monitors should return a 405 if the monitors are configured via YAML" {

        table(
            headers("url", "method", "testBody"),
            row("/api/v2/push-monitors", HttpMethod.POST, monitorCreateDto),
            row("/api/v2/push-monitors/1", HttpMethod.DELETE, null),
            row("/api/v2/push-monitors/1", HttpMethod.PATCH, monitorUpdateDto),
        ).forAll { url, method, testBody ->
            val request = HttpRequest.create<Any>(method, url).apply { testBody?.let { body(it) } }
            val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

            ex.response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
        }
    }
})
