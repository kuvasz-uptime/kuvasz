package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.testutils.getLowLevelClient
import com.kuvaszuptime.kuvasz.testutils.startTestApplication
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.mockk.every
import io.mockk.mockk

class GlobalErrorHandlerTest : BehaviorSpec({

    val crudServiceMock = mockk<MonitorCrudService>()
    val server = startTestApplication(mockBeans = listOf(crudServiceMock))
    val client = server.getLowLevelClient()

    given("an endpoint that accepts a payload") {
        `when`("it is called with an invalid JSON") {
            val request = HttpRequest.POST<String>("/monitor", "not-a-json")
            val exception = shouldThrow<HttpClientResponseException> {
                client.toBlocking()
                    .exchange(request, Argument.of(MonitorPojo::class.java), Argument.of(ServiceError::class.java))
            }
            then("should return a 400 with the correct error message") {
                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "Can't parse the JSON in the payload"
            }
        }

        `when`("it is called with a JSON that contains a non-convertible property") {
            val request =
                HttpRequest.POST<String>("/monitor", "{\"uptimeCheckInterval\":\"not-a-number\"}")
            val exception = shouldThrow<HttpClientResponseException> {
                client.toBlocking()
                    .exchange(request, Argument.of(MonitorPojo::class.java), Argument.of(ServiceError::class.java))
            }
            then("should return a 400 with the correct error message") {
                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldStartWith "Failed to convert argument:"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a PersistenceError") {
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/monitor", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws PersistenceError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .toBlocking()
                    .exchange(request, Argument.of(MonitorPojo::class.java), Argument.of(ServiceError::class.java))
            }
            then("should return a 500 with the correct error message") {
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a SchedulingError") {
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/monitor", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws SchedulingError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .toBlocking()
                    .exchange(request, Argument.of(MonitorPojo::class.java), Argument.of(ServiceError::class.java))
            }
            then("should return a 500 with the correct error message") {
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a DuplicationError") {
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/monitor", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws DuplicationError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .toBlocking()
                    .exchange(request, Argument.of(MonitorPojo::class.java), Argument.of(ServiceError::class.java))
            }
            then("should return a 409 with the correct error message") {
                exception.status shouldBe HttpStatus.CONFLICT
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }
    }
})
