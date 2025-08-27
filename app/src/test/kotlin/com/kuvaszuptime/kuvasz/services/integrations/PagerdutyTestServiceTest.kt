package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerPayload
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.reactivex.rxjava3.core.Single

@SMTPTest
@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class PagerdutyTestServiceTest(
    private val client: PagerdutyAPIClient,
    private val pdTestService: PagerdutyTestService,
) : ShouldSpec({

    context("sendTestMessage") {

        should("call the API to trigger and immediately resolve a test incident") {
            val testIntegrationKey = "3hkl43hkl43h4kl34h3kl4"
            val config = mockk<PagerdutyConfig>(relaxed = true) {
                every { integrationKey } returns testIntegrationKey
            }
            val expectedTriggerRequest = PagerdutyTriggerRequest(
                routingKey = testIntegrationKey,
                dedupKey = "kuvasz-test",
                payload = PagerdutyTriggerPayload(
                    summary = Messages.integrationTestMessage(),
                    source = "Kuvasz Uptime",
                    severity = PagerdutySeverity.WARNING,
                )
            )
            val expectedResolveRequest = PagerdutyResolveRequest(
                routingKey = testIntegrationKey,
                dedupKey = "kuvasz-test",
            )
            val mockClient = getMock(client)
            every { mockClient.triggerAlert(expectedTriggerRequest) } returns Single.just("ok")
            every { mockClient.resolveAlert(expectedResolveRequest) } returns Single.just("ok")

            val result = pdTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verifyOrder {
                mockClient.triggerAlert(expectedTriggerRequest)
                mockClient.resolveAlert(expectedResolveRequest)
            }
        }

        should("return a failed result when the trigger call fails and not call resolve at all") {
            val testIntegrationKey = "3hkl43hkl43h4kl34h3kl4"
            val config = mockk<PagerdutyConfig>(relaxed = true) {
                every { integrationKey } returns testIntegrationKey
            }
            val expectedTriggerRequest = PagerdutyTriggerRequest(
                routingKey = testIntegrationKey,
                dedupKey = "kuvasz-test",
                payload = PagerdutyTriggerPayload(
                    summary = Messages.integrationTestMessage(),
                    source = "Kuvasz Uptime",
                    severity = PagerdutySeverity.WARNING,
                )
            )
            val mockClient = getMock(client)
            every { mockClient.triggerAlert(expectedTriggerRequest) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = pdTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")
            verify(exactly = 1) { mockClient.triggerAlert(expectedTriggerRequest) }
            verify(exactly = 0) { mockClient.resolveAlert(any()) }
        }

        should("return a failed result when the resolve call fails") {
            val testIntegrationKey = "3hkl43hkl43h4kl34h3kl4"
            val config = mockk<PagerdutyConfig>(relaxed = true) {
                every { integrationKey } returns testIntegrationKey
            }
            val expectedTriggerRequest = PagerdutyTriggerRequest(
                routingKey = testIntegrationKey,
                dedupKey = "kuvasz-test",
                payload = PagerdutyTriggerPayload(
                    summary = Messages.integrationTestMessage(),
                    source = "Kuvasz Uptime",
                    severity = PagerdutySeverity.WARNING,
                )
            )
            val expectedResolveRequest = PagerdutyResolveRequest(
                routingKey = testIntegrationKey,
                dedupKey = "kuvasz-test",
            )
            val mockClient = getMock(client)
            every { mockClient.triggerAlert(expectedTriggerRequest) } returns Single.just("ok")
            every { mockClient.resolveAlert(expectedResolveRequest) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = pdTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")
            verifyOrder {
                mockClient.triggerAlert(expectedTriggerRequest)
                mockClient.resolveAlert(expectedResolveRequest)
            }
        }
    }
}) {
    @MockBean(PagerdutyAPIClient::class)
    fun pagerdutyAPIClient(): PagerdutyAPIClient = mockk()
}
