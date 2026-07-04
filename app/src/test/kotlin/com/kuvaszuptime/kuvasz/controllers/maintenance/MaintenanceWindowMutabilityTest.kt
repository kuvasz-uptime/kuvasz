package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
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

@MicronautTest(environments = ["yaml-monitors", "full-integrations-setup", "maintenance-windows"])
class MaintenanceWindowMutabilityTest(
    @param:Client("/") private val client: HttpClient,
) : DatabaseStringSpec({

    val createDto = MaintenanceWindowCreateDto(name = "irrelevant")
    val updateDto = JsonNodeFactory.instance.objectNode().put("enabled", false)

    "all the API endpoints that mutate maintenance windows return a 405 if they are configured via YAML" {
        table(
            headers("url", "method", "testBody"),
            row("/api/v2/maintenance-windows", HttpMethod.POST, createDto),
            row("/api/v2/maintenance-windows/1", HttpMethod.DELETE, null),
            row("/api/v2/maintenance-windows/1", HttpMethod.PATCH, updateDto),
        ).forAll { url, method, testBody ->
            val request = HttpRequest.create<Any>(method, url).apply { testBody?.let { body(it) } }
            val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

            ex.response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
        }
    }
})
