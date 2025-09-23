package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitFirst

/**
 * These tests are focusing on the internal behavior of the controller WITHOUT TAKING AUTHENTICATION INTO ACCOUNT.
 * The tests that take the authentication into account are located in
 * [com.kuvaszuptime.kuvasz.security.AuthenticationTest] and
 * [com.kuvaszuptime.kuvasz.security.DisabledAuthenticationTest]
 */
@MicronautTest
class ExternalStatusPageControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
) : DatabaseBehaviorSpec({

    given("the getDefaultStatusPage() endpoint") {

        `when`("it is called") {

            then("it should return 200 and fetch the data from the underlying service") {
                val mockDataService = getMock(statusPageDataActions)
                every { mockDataService.getCachedDefaultStatusPageData() } returns mockk(relaxed = true)

                val response = client.exchange("/status").awaitFirst()

                response.status shouldBe HttpStatus.OK
                verify(exactly = 1) { mockDataService.getCachedDefaultStatusPageData() }
            }
        }
    }

    given("the getStatusPage(slug) endpoint") {

        val testSlug = "status-page-1"

        `when`("the status page with the given slug exists") {

            then("it should return 200 and fetch the data from the underlying service") {
                val mockService = getMock(statusPageActions)
                val mockDataService = getMock(statusPageDataActions)
                every { mockService.getStatusPageBySlug(testSlug, null) } returns mockk {
                    every { id } returns 123L
                }
                every { mockDataService.getCachedStatusPageData(123) } returns mockk(relaxed = true)

                val response = client.exchange("/status/$testSlug").awaitFirst()

                response.status shouldBe HttpStatus.OK
                verify(exactly = 1) { mockService.getStatusPageBySlug(testSlug, null) }
                verify(exactly = 1) { mockDataService.getCachedStatusPageData(123) }
            }
        }

        `when`("the status page with the given slug does not exist") {

            then("it should return 404 Not Found") {
                val mockService = getMock(statusPageActions)
                val mockDataService = getMock(statusPageDataActions)
                every { mockService.getStatusPageBySlug(testSlug, null) } returns null

                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/status/$testSlug").awaitFirst()
                }
                ex.status shouldBe HttpStatus.NOT_FOUND
                verify(exactly = 1) { mockService.getStatusPageBySlug(testSlug, null) }
                verify(exactly = 0) { mockDataService.getCachedStatusPageData(any()) }
            }
        }
    }
}) {
    @MockBean(StatusPageActions::class)
    fun mockStatusPageActions(): StatusPageActions = mockk()

    @MockBean(StatusPageDataActions::class)
    fun mockStatusPageDataActions(): StatusPageDataActions = mockk()
}
