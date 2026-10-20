package com.kuvaszuptime.kuvasz.security

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.security.oidc.OidcProviderStartupNotice
import com.kuvaszuptime.kuvasz.testutils.KeycloakTestRealm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadataFetcher
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Reproduces https://github.com/micronaut-projects/micronaut-security/issues/1797: if the OIDC provider is unreachable
 * while Kuvasz boots, the OIDC login has to recover by itself once the provider is up, without restarting Kuvasz
 */
class OidcProviderUnavailableAtStartupTest : BehaviorSpec({

    // The provider has to be bound to a port that is known before Kuvasz starts, because the issuer points to it
    val providerPort = ServerSocket(0).use { it.localPort }
    val keycloak = KeycloakTestRealm.newContainer().apply {
        portBindings = listOf("$providerPort:$KEYCLOAK_HTTP_PORT")
    }

    var server: EmbeddedServer? = null
    lateinit var baseUrl: String

    var startupLogs = emptyList<ILoggingEvent>()

    beforeSpec {
        val properties = mapOf(
            "micronaut.security.enabled" to true,
            "admin-auth.api-key" to TEST_API_KEY,
            "micronaut.security.oauth2.clients.oidc.enabled" to true,
            "micronaut.security.oauth2.clients.oidc.client-id" to KeycloakTestRealm.CLIENT_ID,
            "micronaut.security.oauth2.clients.oidc.client-secret" to KeycloakTestRealm.CLIENT_SECRET,
            "micronaut.security.oauth2.clients.oidc.openid.issuer" to
                KeycloakTestRealm.issuerUrl("http://localhost:$providerPort"),
            "micronaut.security.oauth2.clients.oidc.authorization-server" to "keycloak",
        )
        val (embeddedServer, logs) =
            captureLogs(DefaultOpenIdProviderMetadataFetcher::class.java, OidcProviderStartupNotice::class.java) {
                ApplicationContext.run(EmbeddedServer::class.java, properties, "test")
            }
        startupLogs = logs
        server = embeddedServer
        baseUrl = "http://localhost:${embeddedServer.port}"
    }

    afterSpec {
        server?.stop()
        keycloak.stop()
    }

    // The scenarios build on each other: the provider comes up between them, while the same Kuvasz instance keeps
    // running
    given("an OIDC-enabled Kuvasz instance that was started while the OIDC provider was unreachable") {

        `when`("the startup has finished") {

            val fetcherErrors = startupLogs.withIndex().filter { (_, event) ->
                event.loggerName == DefaultOpenIdProviderMetadataFetcher::class.java.name && event.level == Level.ERROR
            }
            val notices = startupLogs.withIndex().filter { (_, event) ->
                event.loggerName == OidcProviderStartupNotice::class.java.name
            }

            then("it should log a warning after the fetcher's errors, explaining that they are harmless") {
                fetcherErrors.shouldNotBeEmpty()
                val notice = notices.shouldHaveSize(1).single()
                notice.value.level shouldBe Level.WARN
                notice.value.formattedMessage shouldContain "are harmless"
                notice.index shouldBeGreaterThan fetcherErrors.maxOf { it.index }
            }
        }

        `when`("the login is initiated while the provider is still unreachable") {

            val redirect = sendWithoutRedirects("$baseUrl/oauth/login/oidc")
            val loginPage = TestBrowser().get("$baseUrl/oauth/login/oidc")

            then("it should redirect to the login page instead of failing with an internal error") {
                redirect.statusCode() shouldBe HttpStatus.SEE_OTHER.code
                redirect.headers().firstValue("location").orElseThrow() shouldBe "/login?oidcUnavailable=true"
            }

            then("the login page should explain that the provider is unavailable") {
                loginPage.statusCode() shouldBe HttpStatus.OK.code
                loginPage.body() shouldContain Messages.oidcProviderUnavailable()
            }
        }

        `when`("the OIDC logout is initiated while the provider is still unreachable") {

            val redirect = sendWithoutRedirects("$baseUrl/oauth/logout")

            then("it should fall back to the local logout") {
                redirect.statusCode() shouldBe HttpStatus.SEE_OTHER.code
                redirect.headers().firstValue("location").orElseThrow() shouldBe "/auth/logout"
            }
        }

        `when`("a user goes through the full login flow after the provider became reachable") {

            keycloak.start()
            val browser = oidcLogin(baseUrl, KeycloakTestRealm.USERNAME, KeycloakTestRealm.PASSWORD)

            then("it lands back on Kuvasz with a JWT cookie carrying the WEB and API roles") {
                val jwt = browser.cookie("JWT").shouldNotBeNull()
                rolesFromJwt(jwt) shouldContainAll listOf(Role.WEB.alias, Role.API.alias)
            }

            then("the session grants access to a secured API endpoint") {
                browser.get("$baseUrl/api/v2/http-monitors").statusCode() shouldBe HttpStatus.OK.code
            }

            then("the session grants access to a secured UI endpoint") {
                browser.get("$baseUrl/http-monitors").statusCode() shouldBe HttpStatus.OK.code
            }
        }

        `when`("the same user logs in again later on") {

            val browser = oidcLogin(baseUrl, KeycloakTestRealm.USERNAME, KeycloakTestRealm.PASSWORD)

            then("the login keeps working") {
                browser.cookie("JWT").shouldNotBeNull()
                browser.get("$baseUrl/api/v2/http-monitors").statusCode() shouldBe HttpStatus.OK.code
            }
        }
    }
})

private const val KEYCLOAK_HTTP_PORT = 8080

private fun sendWithoutRedirects(url: String): HttpResponse<String> =
    HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
        .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
