package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.mocks.generateCredentials
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpHeaders
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

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class AuthenticationTest(
    @Client("/") private val client: HttpClient,
    private val authConfig: AdminAuthConfig
) : BehaviorSpec(
    {

        given("a public API endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/api/v1/health").awaitFirst()
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
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
                    client.exchange("/api/v1/monitors").awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides a wrong API key") {
                val request = HttpRequest.GET<Any>("/api/v1/monitors").header("X-API-KEY", "irrelevant")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right API key") {
                val request = HttpRequest.GET<Any>("/api/v1/monitors").header("X-API-KEY", TEST_API_KEY)
                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }

            `when`("a user is authenticated via a JWT cookie") {
                val jwt = getValidJWT(client, authConfig)

                val request = HttpRequest
                    .GET<Any>("/api/v1/monitors")
                    .header(HttpHeaders.COOKIE, "JWT=$jwt")

                val response = client.exchange(request).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("a secured UI endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/monitors").awaitFirst()

                then("it should return 303 to /login") {
                    response.status() shouldBe HttpStatus.SEE_OTHER
                    response.headers.get(HttpHeaders.LOCATION).shouldNotBeNull().let { locationHeader ->
                        locationHeader shouldBe "/login"
                    }
                }
            }

            `when`("it receives a valid API key") {

                val request = HttpRequest.GET<Any>("/monitors").header("X-API-KEY", TEST_API_KEY)
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
                    HttpRequest.GET<Any>("/monitors").header(HttpHeaders.COOKIE, "JWT=$jwt")
                ).awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
)

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
