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

    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.6")
        .withRealmImportFile("/keycloak/kuvasz-realm.json")

    var server: EmbeddedServer? = null
    lateinit var baseUrl: String

    beforeSpec {
        keycloak.start()

        val properties = mapOf(
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
        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java, properties, "test")
        server = embeddedServer
        baseUrl = "http://localhost:${embeddedServer.port}"
    }

    afterSpec {
        server?.stop()
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
})

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
