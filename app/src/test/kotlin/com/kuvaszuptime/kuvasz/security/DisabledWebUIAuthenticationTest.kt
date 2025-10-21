package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
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
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
) : DatabaseStringSpec() {
    init {
        "all the web UI endpoints should be publicly available" {
            val httpMonitor = createHttpMonitor(httpMonitorRepository)
            val pushMonitor = createPushMonitor(pushMonitorRepository)
            val statusPage = createStatusPage(dslContext, public = false)

            table(
                headers("url"),
                row("/"),
                row("/http-monitors"),
                row("/http-monitors/${httpMonitor.id}"),
                row("/http-monitors/fragments/list"),
                row("/http-monitors/fragments/details-heading/${httpMonitor.id}"),
                row("/http-monitors/fragments/details-uptime-incidents/${httpMonitor.id}"),
                row("/http-monitors/fragments/details-ssl-incidents/${httpMonitor.id}"),
                row("/http-monitors/fragments/stats"),
                row("/push-monitors"),
                row("/push-monitors/${pushMonitor.id}"),
                row("/push-monitors/fragments/list"),
                row("/push-monitors/fragments/details-heading/${pushMonitor.id}"),
                row("/push-monitors/fragments/details-uptime-incidents/${pushMonitor.id}"),
                row("/push-monitors/fragments/stats"),
                row("/settings"),
                row("/integrations"),
                row("/incidents"),
                row("/status-pages"),
                row("/status-pages/${statusPage.id}"),
                row("/status-pages/fragments/list"),
            ).forAll { url ->
                val response = client.exchange(url).awaitFirst()

                response.status shouldBe HttpStatus.OK
            }
        }
    }
}
