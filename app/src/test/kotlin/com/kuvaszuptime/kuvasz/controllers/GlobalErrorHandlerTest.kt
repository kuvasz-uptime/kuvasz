package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.SchedulingException
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
class GlobalErrorHandlerTest(
    @Client("/") client: HttpClient,
    httpMonitorActions: HttpMonitorActions
) : BehaviorSpec({

    given("an endpoint that accepts a payload") {

        `when`("it is called with an invalid JSON") {

            val request = HttpRequest.POST("/api/v2/http-monitors", "not-a-json")
            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(HttpMonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 400 with the correct error message") {

                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBodyAs<ServiceError>().shouldNotBeNull()
                responseBody.message shouldBe "Invalid JSON"
            }
        }

        `when`("it is called with a JSON that contains a non-convertible property") {

            val request =
                HttpRequest.POST("/api/v2/http-monitors", "{\"uptimeCheckInterval\":\"not-a-number\"}")
            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(HttpMonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 400 with the correct error message") {

                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBodyAs<ServiceError>().shouldNotBeNull()
                responseBody.message shouldStartWith "Invalid JSON"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a PersistenceError") {

            val monitorActionsMock = getMock(httpMonitorActions)
            val monitorDto = HttpMonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v2/http-monitors", monitorDto)

            every { monitorActionsMock.createMonitor(any()) } throws PersistenceException("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(HttpMonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 500 with the correct error message") {

                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBodyAs<ServiceError>().shouldNotBeNull()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a SchedulingError") {

            val monitorActionsMock = getMock(httpMonitorActions)
            val monitorDto = HttpMonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v2/http-monitors", monitorDto)

            every { monitorActionsMock.createMonitor(any()) } throws SchedulingException("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(HttpMonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }
            then("should return a 500 with the correct error message") {
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBodyAs<ServiceError>().shouldNotBeNull()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a DuplicationError") {

            val monitorActionsMock = getMock(httpMonitorActions)
            val monitorDto = HttpMonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v2/http-monitors", monitorDto)

            every { monitorActionsMock.createMonitor(any()) } throws DuplicationException("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(HttpMonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 409 with the correct error message") {

                exception.status shouldBe HttpStatus.CONFLICT
                val responseBody = exception.response.getBodyAs<ServiceError>().shouldNotBeNull()
                responseBody.message shouldBe "This is an error message"
            }
        }
    }
}) {
    @MockBean(HttpMonitorActions::class)
    fun httpMonitorActions(): HttpMonitorActions = mockk()
}
