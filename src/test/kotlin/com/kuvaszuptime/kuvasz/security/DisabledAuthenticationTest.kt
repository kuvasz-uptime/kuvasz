package com.kuvaszuptime.kuvasz.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class DisabledAuthenticationTest(
    @Client("/") private val client: HttpClient,
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
                val credentials = UsernamePasswordCredentials(
                    TEST_USERNAME,
                    TEST_PASSWORD
                )
                val request = HttpRequest.POST("/auth/login", credentials)

                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, Any::class.java).awaitFirst()
                }

                then("it should return 404, because the endpoint is not available") {
                    exception.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
        given("a secured API endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/api/v1/monitors").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }

        given("a secured UI endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.exchange("/monitors").awaitFirst()

                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
)
