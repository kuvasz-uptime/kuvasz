package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.security.oidc.OIDC_PROVIDER_NAME
import dasniko.testcontainers.keycloak.KeycloakContainer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * End-to-end test of the OIDC authorization-code flow against a real Keycloak instance
 */
class OidcAuthenticationE2ETest : BehaviorSpec({

    val realm = "kuvasz"
    val clientId = "kuvasz"
    val clientSecret = "kuvasz-client-secret"
    val username = "kuvasz-user"
    val password = "kuvasz-password"
    val allowedEmail = "kuvasz-user@example.com"

    // A user with a verified email that is NOT on the allowlist (see kuvasz-realm.json)
    val strangerUsername = "stranger-user"
    val strangerPassword = "stranger-password"

    // A user whose email IS on the allowlist, but has not been verified (see kuvasz-realm.json)
    val unverifiedUsername = "unverified-user"
    val unverifiedPassword = "unverified-password"
    val unverifiedEmail = "unverified@example.com"

    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.6")
        .withRealmImportFile("/keycloak/kuvasz-realm.json")

    var server: EmbeddedServer? = null
    lateinit var baseUrl: String

    // A second instance that restricts logins to an email allowlist (with the default verified-email requirement)
    var allowlistServer: EmbeddedServer? = null
    lateinit var allowlistBaseUrl: String

    beforeSpec {
        keycloak.start()

        val baseProperties = mapOf(
            "micronaut.security.enabled" to true,
            "admin-auth.api-key" to TEST_API_KEY,
            "admin-auth.username" to TEST_USERNAME,
            "admin-auth.password" to TEST_PASSWORD,
            "micronaut.security.oauth2.clients.oidc.enabled" to true,
            "micronaut.security.oauth2.clients.oidc.client-id" to clientId,
            "micronaut.security.oauth2.clients.oidc.client-secret" to clientSecret,
            "micronaut.security.oauth2.clients.oidc.openid.issuer" to "${keycloak.authServerUrl}/realms/$realm",
            // Modern Keycloak issuers (/realms/...) aren't auto-detected, so the provider has to be set explicitly
            // for the end-session endpoint (and thus the /oauth/logout route) to be resolved.
            "micronaut.security.oauth2.clients.oidc.authorization-server" to "keycloak",
        )
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java, baseProperties, "test")
        server = embeddedServer
        baseUrl = "http://localhost:${embeddedServer.port}"

        // The allowlist contains the verified user, plus an address whose user has NOT verified it - the latter must
        // still be rejected, because the verified-email requirement is on by default.
        val allowlistProperties = baseProperties + mapOf(
            "admin-auth.oidc.allowed-emails" to "$allowedEmail,$unverifiedEmail",
        )
        val allowlistEmbedded = ApplicationContext.run(EmbeddedServer::class.java, allowlistProperties, "test")
        allowlistServer = allowlistEmbedded
        allowlistBaseUrl = "http://localhost:${allowlistEmbedded.port}"
    }

    afterSpec {
        server?.stop()
        allowlistServer?.stop()
        keycloak.stop()
    }

    given("an OIDC-enabled Kuvasz instance backed by Keycloak") {

        `when`("a user goes through the full authorization-code login flow") {

            val browser = TestBrowser()

            // 1. Hitting the OIDC login endpoint redirects all the way to the Keycloak login page
            val loginPage = browser.get("$baseUrl/oauth/login/oidc")

            // 2. Submit the Keycloak login form with valid credentials, which redirects back to the Kuvasz callback
            val formAction = extractLoginFormAction(loginPage.body())
            val formBody = "username=${username.urlEncoded()}&password=${password.urlEncoded()}&credentialId="
            val afterLogin = browser.postForm(formAction, formBody)

            then("it lands back on the Kuvasz instance with a JWT cookie set") {
                afterLogin.statusCode() shouldBe 200
                afterLogin.uri().host shouldBe "localhost"
                browser.cookie("JWT").shouldNotBeNull()
            }

            then("the JWT cookie carries the WEB and API roles") {
                val roles = rolesFromJwt(browser.cookie("JWT").shouldNotBeNull())
                roles shouldContainAll listOf(Role.WEB.alias, Role.API.alias)
            }

            then("the JWT cookie grants access to a secured API endpoint (ROLE_API)") {
                val response = browser.get("$baseUrl/api/v2/http-monitors")
                response.statusCode() shouldBe 200
            }

            then("the JWT cookie grants access to a secured UI endpoint (ROLE_WEB)") {
                val response = browser.get("$baseUrl/http-monitors")
                response.statusCode() shouldBe 200
            }

            then("the settings API exposes the publishable OIDC provider settings") {
                val response = browser.get("$baseUrl/api/v2/settings/")
                response.statusCode() shouldBe 200

                val oidc = objectMapper.readTree(response.body())["authentication"][OIDC_PROVIDER_NAME]
                oidc.shouldNotBeNull()
                oidc["issuer"].asString() shouldBe "${keycloak.authServerUrl}/realms/$realm"
                oidc["clientId"].asString() shouldBe clientId
            }

            then("the settings page renders the OIDC provider setup details") {
                val response = browser.get("$baseUrl/settings")
                response.statusCode() shouldBe 200
                response.body() shouldContain "/oauth/callback/oidc"
                response.body() shouldContain clientId
                response.body() shouldContain "${keycloak.authServerUrl}/realms/$realm"
            }

            then("the rendered navigation points the sign-out link at the OIDC logout endpoint") {
                val response = browser.get("$baseUrl/")
                response.statusCode() shouldBe 200
                response.body() shouldContain "/oauth/logout"
            }

            then("hitting the OIDC logout endpoint redirects to the Keycloak end-session endpoint") {
                val jwt = browser.cookie("JWT").shouldNotBeNull()
                val response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(
                        HttpRequest.newBuilder(URI.create("$baseUrl/oauth/logout"))
                            .header("Cookie", "JWT=$jwt")
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )

                response.statusCode() shouldBe 302
                val location = response.headers().firstValue("location").get()
                location shouldContain "${keycloak.authServerUrl}/realms/$realm/protocol/openid-connect/logout"
                location shouldContain "post_logout_redirect_uri"
            }
        }

        `when`("an anonymous user requests a secured API endpoint") {
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("$baseUrl/api/v2/http-monitors")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            then("it is rejected with 401") {
                response.statusCode() shouldBe 401
            }
        }

        `when`("the login page is rendered") {
            val response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(
                    HttpRequest.newBuilder(URI.create("$baseUrl/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )

            then("it shows the OIDC login button instead of the username/password form") {
                response.statusCode() shouldBe 200
                response.body() shouldContain "/oauth/login/oidc"
            }
        }
    }

    given("an OIDC-enabled Kuvasz instance with an email allowlist") {

        `when`("an allow-listed user with a verified email completes the login flow") {
            val browser = oidcLogin(allowlistBaseUrl, username, password)

            then("a JWT cookie is set carrying the WEB and API roles") {
                val jwt = browser.cookie("JWT").shouldNotBeNull()
                rolesFromJwt(jwt) shouldContainAll listOf(Role.WEB.alias, Role.API.alias)
            }

            then("the session grants access to a secured API endpoint") {
                browser.get("$allowlistBaseUrl/api/v2/http-monitors").statusCode() shouldBe 200
            }
        }

        `when`("a user with a verified email that is NOT on the allowlist completes the login flow") {
            val browser = oidcLogin(allowlistBaseUrl, strangerUsername, strangerPassword)

            then("no JWT cookie is set, the login is rejected") {
                browser.cookie("JWT") shouldBe null
            }

            then("the user cannot access a secured API endpoint") {
                browser.get("$allowlistBaseUrl/api/v2/http-monitors").statusCode() shouldBe 401
            }
        }

        `when`("an allow-listed user whose email is NOT verified completes the login flow") {
            val browser = oidcLogin(allowlistBaseUrl, unverifiedUsername, unverifiedPassword)

            then("the login is rejected, because the verified-email requirement is on by default") {
                browser.cookie("JWT") shouldBe null
                browser.get("$allowlistBaseUrl/api/v2/http-monitors").statusCode() shouldBe 401
            }
        }
    }
})

/**
 * Drives the full OIDC authorization-code flow: hits the OIDC login endpoint, submits the Keycloak login form with the
 * given credentials, and returns the [TestBrowser] holding whatever cookies ended up being set (a JWT on success,
 * none on a rejected login).
 */
private fun oidcLogin(baseUrl: String, username: String, password: String): TestBrowser {
    val browser = TestBrowser()
    val loginPage = browser.get("$baseUrl/oauth/login/oidc")
    val formAction = extractLoginFormAction(loginPage.body())
    val formBody = "username=${username.urlEncoded()}&password=${password.urlEncoded()}&credentialId="
    browser.postForm(formAction, formBody)
    return browser
}

/**
 * A minimal stateful HTTP "browser" that keeps a single cookie jar and follows redirects manually, replaying every
 * stored cookie on every request regardless of its path/domain attributes. Good enough for a same-host E2E flow.
 */
private class TestBrowser {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    private val cookies = mutableMapOf<String, String>()

    fun cookie(name: String): String? = cookies[name]

    fun get(url: String): HttpResponse<String> =
        send(requestBuilder(url).GET().build())

    fun postForm(url: String, body: String): HttpResponse<String> =
        send(
            requestBuilder(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        )

    private fun requestBuilder(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url)).apply {
            if (cookies.isNotEmpty()) {
                header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
        }

    private fun send(request: HttpRequest): HttpResponse<String> {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString())
        harvestCookies(response)

        var hops = 0
        while (response.statusCode() in 300..399 && hops < MAX_REDIRECTS) {
            val location = response.headers().firstValue("location").orElseThrow {
                IllegalStateException("Redirect response without a Location header")
            }
            val next = response.uri().resolve(location)
            response = client.send(requestBuilder(next.toString()).GET().build(), HttpResponse.BodyHandlers.ofString())
            harvestCookies(response)
            hops++
        }
        return response
    }

    private fun harvestCookies(response: HttpResponse<*>) {
        response.headers().allValues("set-cookie").forEach { setCookie ->
            val pair = setCookie.substringBefore(";")
            val name = pair.substringBefore("=").trim()
            val value = pair.substringAfter("=", "")
            if (value.isEmpty()) cookies.remove(name) else cookies[name] = value
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 10
    }
}

private val objectMapper = jacksonObjectMapper()

private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun rolesFromJwt(jwt: String): List<String> {
    val payload = jwt.split(".")[1]
    val decoded = String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
    val claims = objectMapper.readValue<Map<String, Any>>(decoded)
    @Suppress("UNCHECKED_CAST")
    return (claims["roles"] as? List<String>).orEmpty()
}

private val LOGIN_FORM_ACTION_REGEX = Regex("""<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"""")

private fun extractLoginFormAction(html: String): String {
    val rawAction = LOGIN_FORM_ACTION_REGEX.find(html)?.groupValues?.get(1)
        ?: error("Could not find the Keycloak login form action in the returned HTML")
    return rawAction.replace("&amp;", "&")
}
