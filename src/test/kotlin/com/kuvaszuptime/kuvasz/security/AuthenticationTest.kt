package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.mocks.generateCredentials
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.rxjava3.http.client.Rx3HttpClient
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
class AuthenticationTest(
    @Client("/") private val client: Rx3HttpClient,
    private val authConfig: AdminAuthConfig
) : BehaviorSpec(
    {
        given("a public endpoint") {

            `when`("an anonymous user calls it") {
                val response = client.toBlocking().exchange<Any>("/health")
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
        given("the login endpoint") {

            `when`("the user provides the right credentials") {
                val credentials = generateCredentials(authConfig, valid = true)
                val request = HttpRequest.POST("/login", credentials)
                val response = client.toBlocking().exchange(request, BearerAccessRefreshToken::class.java)
                val token = response.body()!!
                val parsedJwt = JWTParser.parse(token.accessToken)
                then("it should return a signed access token for the given user") {
                    response.status shouldBe HttpStatus.OK
                    token.username shouldBe credentials.username
                    token.accessToken shouldNotBe null
                    parsedJwt.shouldBeInstanceOf<SignedJWT>()
                }
            }

            `when`("a user provides bad credentials") {
                val credentials = generateCredentials(authConfig, valid = false)
                val request = HttpRequest.POST("/login", credentials)
                val exception = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, BearerAccessRefreshToken::class.java)
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
        given("an authenticated endpoint") {

            `when`("an anonymous user calls it") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any>("/monitors")
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a user provides the right credentials") {
                val credentials = generateCredentials(authConfig, valid = true)
                val loginRequest = HttpRequest.POST("/login", credentials)
                val loginResponse = client.toBlocking().exchange(loginRequest, BearerAccessRefreshToken::class.java)
                val token = loginResponse.body()!!

                val request = HttpRequest.GET<Any>("/monitors").bearerAuth(token.accessToken)
                val response = client.toBlocking().exchange<Any, Any>(request)
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
)
