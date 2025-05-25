package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
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
    @Client("/") client: HttpClient,
    monitorRepository: MonitorRepository,
) : DatabaseStringSpec({

    "all the web UI endpoints should be publicly available" {
        val monitor = createMonitor(monitorRepository)

        table(
            headers("url"),
            row("/"),
            row("/monitors"),
            row("/monitors/${monitor.id}"),
            row("/fragments/monitors/list"),
            row("/fragments/monitors/${monitor.id}/details-heading"),
            row("/fragments/monitors/${monitor.id}/details-uptime-events"),
            row("/fragments/monitors/${monitor.id}/details-ssl-events"),
        ).forAll { url ->
            val response = client.exchange(url).awaitFirst()

            response.status shouldBe HttpStatus.OK
        }
    }
})
