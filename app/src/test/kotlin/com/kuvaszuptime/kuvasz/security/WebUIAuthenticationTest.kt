package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.views.htmx.http.HtmxRequestHeaders
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class WebUIAuthenticationTest(
    @param:Client("/") private val client: HttpClient,
    private val authConfig: AdminAuthConfig,
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    icmpMonitorRepository: IcmpMonitorRepository,
) : DatabaseStringSpec() {
    init {

        "all the web UI endpoints should be secured - anonymous user" {

            table(
                headers("url"),
                row("/"),
                row("/http-monitors"),
                row("/http-monitors/1"),
                row("/http-monitors/fragments/list"),
                row("/http-monitors/fragments/details-heading/1"),
                row("/http-monitors/fragments/details-uptime-incidents/1"),
                row("/http-monitors/fragments/details-ssl-incidents/1"),
                row("/http-monitors/fragments/stats"),
                row("/push-monitors"),
                row("/push-monitors/1"),
                row("/push-monitors/fragments/list"),
                row("/push-monitors/fragments/details-heading/1"),
                row("/push-monitors/fragments/details-uptime-incidents/1"),
                row("/push-monitors/fragments/stats"),
                row("/icmp-monitors"),
                row("/icmp-monitors/1"),
                row("/icmp-monitors/fragments/list"),
                row("/icmp-monitors/fragments/details-heading/1"),
                row("/icmp-monitors/fragments/details-uptime-incidents/1"),
                row("/icmp-monitors/fragments/stats"),
                row("/settings"),
                row("/integrations"),
                row("/incidents"),
                row("/status-pages"),
                row("/status-pages/1"),
                row("/status-pages/fragments/list"),
                row("/maintenance-windows"),
                row("/maintenance-windows/1"),
                row("/maintenance-windows/fragments/list"),
                row("/maintenance-windows/fragments/details-heading/1"),
            ).forAll { url ->
                val response = client.exchange(url).awaitFirst()

                response.status shouldBe HttpStatus.SEE_OTHER
                response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                    locationHeader shouldBe "/login"
                }
            }
        }

        "all the web UI endpoints should be secured - valid API key is used" {

            val cases = table(
                headers("url"),
                row("/"),
                row("/http-monitors"),
                row("/http-monitors/1"),
                row("/http-monitors/fragments/list"),
                row("/http-monitors/fragments/details-heading/1"),
                row("/http-monitors/fragments/details-uptime-incidents/1"),
                row("/http-monitors/fragments/details-ssl-incidents/1"),
                row("/http-monitors/fragments/stats"),
                row("/push-monitors"),
                row("/push-monitors/1"),
                row("/push-monitors/fragments/list"),
                row("/push-monitors/fragments/details-heading/1"),
                row("/push-monitors/fragments/details-uptime-incidents/1"),
                row("/push-monitors/fragments/stats"),
                row("/icmp-monitors"),
                row("/icmp-monitors/1"),
                row("/icmp-monitors/fragments/list"),
                row("/icmp-monitors/fragments/details-heading/1"),
                row("/icmp-monitors/fragments/details-uptime-incidents/1"),
                row("/icmp-monitors/fragments/stats"),
                row("/settings"),
                row("/integrations"),
                row("/incidents"),
                row("/status-pages"),
                row("/status-pages/1"),
                row("/status-pages/fragments/list"),
                row("/maintenance-windows"),
                row("/maintenance-windows/1"),
                row("/maintenance-windows/fragments/list"),
                row("/maintenance-windows/fragments/details-heading/1"),
            )
            cases.forAll { url ->
                val request = HttpRequest.GET<Any>(url).header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                response.status shouldBe HttpStatus.SEE_OTHER
                response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                    locationHeader shouldBe "/login"
                }
            }
            cases.forAll { url ->
                val request = HttpRequest.GET<Any>(url).bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                response.status shouldBe HttpStatus.SEE_OTHER
                response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                    locationHeader shouldBe "/login"
                }
            }
        }

        "all the web endpoints should be accessible with a valid JWT" {

            val jwt = getValidJWT(client, authConfig)
            val httpMonitor = createHttpMonitor(httpMonitorRepository)
            val pushMonitor = createPushMonitor(pushMonitorRepository)
            val icmpMonitor = createIcmpMonitor(icmpMonitorRepository)
            val statusPage = createStatusPage(dslContext, public = false)
            val maintenanceWindow = createMaintenanceWindow(dslContext, cron = "0 2 * * *", duration = "PT1H")

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
                row("/icmp-monitors"),
                row("/icmp-monitors/${icmpMonitor.id}"),
                row("/icmp-monitors/fragments/list"),
                row("/icmp-monitors/fragments/details-heading/${icmpMonitor.id}"),
                row("/icmp-monitors/fragments/details-uptime-incidents/${icmpMonitor.id}"),
                row("/icmp-monitors/fragments/stats"),
                row("/settings"),
                row("/integrations"),
                row("/incidents"),
                row("/status-pages"),
                row("/status-pages/${statusPage.id}"),
                row("/status-pages/fragments/list"),
                row("/maintenance-windows"),
                row("/maintenance-windows/${maintenanceWindow.id}"),
                row("/maintenance-windows/fragments/list"),
                row("/maintenance-windows/fragments/details-heading/${maintenanceWindow.id}"),
            ).forAll { url ->
                val response = client.exchange(
                    HttpRequest.GET<Any>(url).header(HttpHeaders.COOKIE, "JWT=$jwt")
                ).awaitFirst()

                response.status shouldBe HttpStatus.OK
            }
        }

        "the sign-out link points to the local logout endpoint when OIDC is disabled" {
            val jwt = getValidJWT(client, authConfig)
            val response = client.exchange(
                HttpRequest.GET<Any>("/").header(HttpHeaders.COOKIE, "JWT=$jwt"),
                String::class.java,
            ).awaitFirst()

            response.status shouldBe HttpStatus.OK
            response.body().shouldNotBeNull().let { body ->
                body shouldContain "/auth/logout"
                body shouldNotContain "/oauth/logout"
            }
        }

        "already authenticated request against /login should be redirected to /" {
            val jwt = getValidJWT(client, authConfig)
            val request = HttpRequest.GET<Any>("/login").header(HttpHeaders.COOKIE, "JWT=$jwt")

            val response = client.exchange(request).awaitFirst()

            response.status shouldBe HttpStatus.SEE_OTHER
            response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                locationHeader shouldBe "/"
            }
        }

        "anonymous HTMX requests should be redirected to the login page with a 204 and a specific header" {
            val request = HttpRequest.GET<Any>("/").header(HtmxRequestHeaders.HX_REQUEST, "true")
            val response = client.exchange(request).awaitFirst()

            response.status shouldBe HttpStatus.NO_CONTENT
            response.headers.get(HtmxResponseHeaders.HX_REDIRECT).shouldNotBeNull().let { redirectHeader ->
                redirectHeader shouldBe "/login"
            }
        }
    }
}
