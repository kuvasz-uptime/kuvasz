package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.generateCredentials
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

const val TEST_API_KEY = "Api1234567890123"
const val TEST_USERNAME = "test-user"
const val TEST_PASSWORD = "test-pass-test-pass-test-pass"

@MicronautTest(environments = ["enabled-metrics-prometheus"])
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class AuthenticationTest(
    @Client("/") client: HttpClient,
    authConfig: AdminAuthConfig,
) : BehaviorSpec(
    {

        given("a public API endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/api/v2/health").awaitFirst()
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("the heartbeat signaling endpoints") {

            `when`("an anonymous user calls it") {
                val heartbeatResponseGet = client.exchange("/api/v2/push-monitors/heartbeats/12345678").awaitFirst()
                val failureResponseGet = client
                    .exchange("/api/v2/push-monitors/heartbeats/12345678/failure")
                    .awaitFirst()
                val heartbeatResponsePost = client
                    .exchange(
                        HttpRequest.create<Any>(HttpMethod.POST, "/api/v2/push-monitors/heartbeats/12345678")
                    )
                    .awaitFirst()
                val failureResponsePost = client
                    .exchange(
                        HttpRequest.create<Any>(HttpMethod.POST, "/api/v2/push-monitors/heartbeats/12345678/failure")
                    )
                    .awaitFirst()

                then("it should return 200") {
                    heartbeatResponseGet.status shouldBe HttpStatus.ACCEPTED
                    failureResponseGet.status shouldBe HttpStatus.ACCEPTED
                    heartbeatResponsePost.status shouldBe HttpStatus.ACCEPTED
                    failureResponsePost.status shouldBe HttpStatus.ACCEPTED
                }
            }
        }

        given("the /auth/login endpoint") {

            `when`("the user provides the right credentials") {
                val credentials = generateCredentials(authConfig, valid = true)
                val request = HttpRequest.POST("/auth/login", credentials)
                val response = client.exchange(request, String::class.java).awaitFirst()

                then("it should set the JWT cookie and redirect to /") {
                    response.status shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/"
                    }
                    response.headers.get(HttpHeaders.SET_COOKIE).shouldNotBeNull().let { setCookieHeader ->
                        setCookieHeader shouldContain "JWT="
                    }
                }
            }

            `when`("a user provides bad credentials") {
                val credentials = generateCredentials(authConfig, valid = false)
                val request = HttpRequest.POST("/auth/login", credentials)
                val response = client.exchange(request, String::class.java).awaitFirst()

                then("it should not set the JWT cookie and redirect to login with an error") {
                    response.status shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/login?error=true"
                    }
                    response.headers.get(HttpHeaders.SET_COOKIE).shouldBeNull()
                }
            }
        }

        given("a secured API endpoint") {

            `when`("an anonymous user calls it") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/http-monitors").awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/api/v2/http-monitors").header("X-API-KEY", "irrelevant")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/api/v2/http-monitors").bearerAuth("irrelevant")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/api/v2/http-monitors").header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/api/v2/http-monitors").bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/api/v2/http-monitors")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("the /prometheus API endpoint") {

            `when`("an anonymous user calls it") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/prometheus").awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/api/v2/prometheus").header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/api/v2/prometheus").bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("a secured UI endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/http-monitors").awaitFirst()

                then("it should return 303 to /login") {
                    response.status() shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/login"
                    }
                }
            }

            `when`("it receives a valid API key in the X-API-KEY header") {

                val request = HttpRequest.GET<Any>("/http-monitors").header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 303 to /login") {
                    response.status shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/login"
                    }
                }
            }

            `when`("it receives a valid API key in the Authorization header") {

                val request = HttpRequest.GET<Any>("/http-monitors").bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 303 to /login") {
                    response.status shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/login"
                    }
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val jwt = getValidJWT(client, authConfig)
                val response = client.exchange(
                    HttpRequest.GET<Any>("/http-monitors").header(HttpHeaders.COOKIE, "JWT=$jwt")
                ).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
)

@MicronautTest(environments = ["full-default-status-page-config"])
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class PublicStatusPageAuthenticationTest(
    @Client("/") client: HttpClient,
    authConfig: AdminAuthConfig,
) : DatabaseBehaviorSpec() {
    init {

        given("a public default status page") {

            `when`("an anonymous user requests it") {
                val response = client.exchange("/status").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("an anonymous user requests it - API") {
                val response = client.exchange("/api/v2/status-pages/0/details").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/status").header("X-API-KEY", "irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header("X-API-KEY", "irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/status").bearerAuth("irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the Authorization header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .bearerAuth("irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/status").header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/status").bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/status")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie - API") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("a public custom status page") {

            `when`("an anonymous user requests it") {
                val statusPage = createStatusPage(dslContext, public = true)
                val response = client.exchange("/status/${statusPage.slug}").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("an anonymous user requests it - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val response = client.exchange("/api/v2/status-pages/${statusPage.id}/details").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header("X-API-KEY", "irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header("X-API-KEY", "irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the Authorization header") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .bearerAuth("irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides a wrong API key in the Authorization header - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .bearerAuth("irrelevant")
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val statusPage = createStatusPage(dslContext, public = true)
                val jwt = getValidJWT(client, authConfig)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie - API") {
                val statusPage = createStatusPage(dslContext, public = true)
                val jwt = getValidJWT(client, authConfig)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
}

@MicronautTest(environments = ["private-default-status-page-config"])
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class PrivateStatusPageAuthenticationTest(
    @Client("/") client: HttpClient,
    authConfig: AdminAuthConfig,
) : DatabaseBehaviorSpec() {
    init {

        given("a private default status page") {

            `when`("an anonymous user requests it") {
                val ex = shouldThrow<HttpClientResponseException> { client.exchange("/status").awaitFirst() }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("an anonymous user requests it - API") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/status-pages/0/details").awaitFirst()
                }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/status").header("X-API-KEY", "irrelevant")
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header("X-API-KEY", "irrelevant")
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/status").bearerAuth("irrelevant")
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides a wrong API key in the Authorization header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .bearerAuth("irrelevant")
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val request = HttpRequest.GET<Any>("/status").header("X-API-KEY", TEST_API_KEY)
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val request = HttpRequest.GET<Any>("/status").bearerAuth(TEST_API_KEY)
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides the right API key in the Authorization header - API") {
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/status")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie - API") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/0/details")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("a private custom status page") {

            `when`("an anonymous user requests it") {
                val statusPage = createStatusPage(dslContext, public = false)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/status/${statusPage.slug}").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("an anonymous user requests it - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/status-pages/${statusPage.id}/details").awaitFirst()
                }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header("X-API-KEY", "irrelevant")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides a wrong API key in the X-API-KEY header - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header("X-API-KEY", "irrelevant")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key in the Authorization header") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .bearerAuth("irrelevant")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides a wrong API key in the Authorization header - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .bearerAuth("irrelevant")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 401") {
                    ex.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header("X-API-KEY", TEST_API_KEY)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides the right API key in the X-API-KEY header - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user provides the right API key in the Authorization header") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .bearerAuth(TEST_API_KEY)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("a user provides the right API key in the Authorization header - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .bearerAuth(TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val statusPage = createStatusPage(dslContext, public = false)
                val jwt = getValidJWT(client, authConfig)
                val request = HttpRequest
                    .GET<Any>("/status/${statusPage.slug}")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie - API") {
                val statusPage = createStatusPage(dslContext, public = false)
                val jwt = getValidJWT(client, authConfig)
                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/${statusPage.id}/details")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
}

suspend fun getValidJWT(client: HttpClient, authConfig: AdminAuthConfig): String {
    val credentials = generateCredentials(authConfig, valid = true)
    val request = HttpRequest.POST("/auth/login", credentials)
    val response = client.exchange(request, String::class.java).awaitFirst()
    val jwt = response.headers.get(HttpHeaders.SET_COOKIE).let { cookieHeader ->
        cookieHeader
            ?.split(";")
            ?.firstOrNull { it.startsWith("JWT=") }
            ?.substringAfter("JWT=")
    }
    jwt.shouldNotBeNull()

    return jwt
}
