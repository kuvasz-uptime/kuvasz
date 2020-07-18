package com.akobor.kuvasz

import com.akobor.kuvasz.utils.addAuthenticationHeader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class AuthenticationTest(@Client("/") private val client: RxHttpClient) : BehaviorSpec() {
    init {
        given("a public endpoint") {
            `when`("an anonymous user calls it") {
                val response = client.exchange("/health").blockingFirst()
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
        given("an authenticated endpoint") {
            `when`("an anonymous user calls it") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/hello").blockingFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }
            `when`("a user provides bad credentials") {
                val exception = shouldThrow<HttpClientResponseException> {
                    val request = HttpRequest.GET<Any>("/hello").addAuthenticationHeader(withValidCredentials = false)
                    client.exchange(request).blockingFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }
            `when`("a user provides the right credentials") {
                val request = HttpRequest.GET<Any>("/hello").addAuthenticationHeader(withValidCredentials = true)
                val response = client.exchange(request).blockingFirst()
                then("it should return 200") {
                    response.status shouldBe HttpStatus.OK
                }
            }
        }
    }
}
