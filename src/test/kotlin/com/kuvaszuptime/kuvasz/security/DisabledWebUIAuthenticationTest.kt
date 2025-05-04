package com.kuvaszuptime.kuvasz.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class DisabledWebUIAuthenticationTest(
    @Client("/") private val client: HttpClient,
) : StringSpec({

    "all the web UI endpoints should be publicly available" {

        table(
            headers("url"),
            row("/"),
            row("/monitors"),
            row("/fragments/monitor-table"),
        ).forAll { url ->
            val response = client.exchange(url).awaitFirst()

            response.status shouldBe HttpStatus.OK
        }
    }
})
