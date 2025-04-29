package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import com.kuvaszuptime.kuvasz.models.SchedulingError
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
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
    monitorCrudService: MonitorCrudService
) : BehaviorSpec({

    given("an endpoint that accepts a payload") {

        `when`("it is called with an invalid JSON") {

            val request = HttpRequest.POST("/api/v1/monitors", "not-a-json")
            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(MonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 400 with the correct error message") {

                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "Invalid JSON"
            }
        }

        `when`("it is called with a JSON that contains a non-convertible property") {

            val request =
                HttpRequest.POST("/api/v1/monitors", "{\"uptimeCheckInterval\":\"not-a-number\"}")
            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(MonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 400 with the correct error message") {

                exception.status shouldBe HttpStatus.BAD_REQUEST
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldStartWith "Failed to convert argument:"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a PersistenceError") {

            val crudServiceMock = getMock(monitorCrudService)
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v1/monitors", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws PersistenceError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(MonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 500 with the correct error message") {

                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a SchedulingError") {

            val crudServiceMock = getMock(monitorCrudService)
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v1/monitors", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws SchedulingError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(MonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }
            then("should return a 500 with the correct error message") {
                exception.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }

        `when`("it is called with a valid body but the underlying logic throws a DuplicationError") {

            val crudServiceMock = getMock(monitorCrudService)
            val monitorDto = MonitorCreateDto(
                name = "test",
                url = "https://valid-url.com",
                uptimeCheckInterval = 60
            )
            val request = HttpRequest.POST("/api/v1/monitors", monitorDto)

            every { crudServiceMock.createMonitor(any()) } throws DuplicationError("This is an error message")

            val exception = shouldThrow<HttpClientResponseException> {
                client
                    .exchange(request, Argument.of(MonitorDto::class.java), Argument.of(ServiceError::class.java))
                    .awaitFirst()
            }

            then("should return a 409 with the correct error message") {

                exception.status shouldBe HttpStatus.CONFLICT
                val responseBody = exception.response.getBody(ServiceError::class.java).get()
                responseBody.message shouldBe "This is an error message"
            }
        }
    }
}) {
    @MockBean(MonitorCrudService::class)
    fun monitorCrudService(): MonitorCrudService {
        return mockk()
    }
}
