package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
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

@MicronautTest(environments = ["yaml-monitors", "full-integrations-setup", "status-pages"])
class StatusPageMutabilityTest(
    @param:Client("/") private val client: HttpClient,
) : DatabaseStringSpec({

    val statusPageCreateDto = StatusPageCreateDto(
        title = "irrelevant",
        slug = "irrelevant",
        monitors = listOf(
            MonitorID(MonitorType.HTTP_SSL, "test1").toString()
        ),
    )
    val statusPageUpdateDto = JsonNodeFactory.instance.objectNode().put(StatusPageUpdateDto::public.name, false)

    "all the API endpoints that mutate status pages should return a 405 if the pages are configured via YAML" {

        table(
            headers("url", "method", "testBody"),
            row("/api/v2/status-pages", HttpMethod.POST, statusPageCreateDto),
            row("/api/v2/status-pages/1", HttpMethod.DELETE, null),
            row("/api/v2/status-pages/1", HttpMethod.PATCH, statusPageUpdateDto),
        ).forAll { url, method, testBody ->
            val request = HttpRequest.create<Any>(method, url).apply { testBody?.let { body(it) } }
            val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

            ex.response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
        }
    }
})
