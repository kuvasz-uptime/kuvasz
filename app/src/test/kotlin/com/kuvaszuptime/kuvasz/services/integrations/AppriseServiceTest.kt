package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.AppriseMessageFactory
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.AppriseMessage
import com.kuvaszuptime.kuvasz.models.handlers.AppriseNotificationConfig
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class AppriseServiceTest(
    private val client: AppriseClient,
    private val messageFactory: AppriseMessageFactory,
    private val appriseService: AppriseService,
) : ShouldSpec({

    val testUrl = "http://apprise-host:8000/notify/kuvasz"

    fun config(
        tag: String? = null,
        targetUrls: List<String>? = null,
        headers: Map<String, String>? = null,
    ) = mockk<AppriseNotificationConfig>(relaxed = true) {
        every { url } returns testUrl
        every { this@mockk.tag } returns tag
        every { this@mockk.targetUrls } returns targetUrls
        every { requestHeaders } returns headers
    }

    context("sendTestMessage") {

        should("call the client with the test notification") {
            val expectedMessage = messageFactory.testMessage()
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) } returns Single.just("OK")

            val result = appriseService.sendTestMessage(config()).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()

            verify(exactly = 1) { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) }
        }

        should("return a failed result when the client call fails") {
            val expectedMessage = messageFactory.testMessage()
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = appriseService.sendTestMessage(config()).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")

            verify(exactly = 4) { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) }
        }
    }

    context("sendEvent") {

        val monitor = HttpMonitorRecord()
            .setId(1111)
            .setName("test_monitor")
            .setUrl("https://test.url")
            .setSensitiveUrl(false)
        val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)

        should("build the notification for the given event and post it to the configured URL") {
            val expectedMessage = messageFactory.fromUptimeEvent(event)
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) } returns Single.just("OK")

            appriseService.sendEvent(config(), event).blockingGet() shouldBe "OK"

            verify(exactly = 1) { mockClient.sendMessage(testUrl.toUri(), emptyMap(), expectedMessage) }
        }

        should("stamp the tag and the target URLs of the config onto the notification") {
            val mockClient = getMock(client)
            val messageSlot = slot<AppriseMessage>()
            every { mockClient.sendMessage(any(), any(), capture(messageSlot)) } returns Single.just("OK")

            val target = config(tag = "devops team-a, oncall", targetUrls = listOf("slack://A/B/C", "json://host"))
            appriseService.sendEvent(target, event).blockingGet() shouldBe "OK"

            messageSlot.captured.tag shouldBe "devops team-a, oncall"
            messageSlot.captured.urls shouldBe listOf("slack://A/B/C", "json://host")
        }

        should("drop the target URLs entirely when the config has none") {
            val mockClient = getMock(client)
            val messageSlot = slot<AppriseMessage>()
            every { mockClient.sendMessage(any(), any(), capture(messageSlot)) } returns Single.just("OK")

            appriseService.sendEvent(config(targetUrls = emptyList()), event).blockingGet() shouldBe "OK"

            messageSlot.captured.urls shouldBe null
            messageSlot.captured.tag shouldBe null
        }

        should("forward the configured request headers to the client") {
            val mockClient = getMock(client)
            val headersSlot = slot<Map<String, String>>()
            every { mockClient.sendMessage(any(), capture(headersSlot), any()) } returns Single.just("OK")

            val headers = mapOf("Authorization" to "Bearer apprise-token")
            appriseService.sendEvent(config(headers = headers), event).blockingGet() shouldBe "OK"

            headersSlot.captured shouldBe headers
        }
    }
}) {
    @MockBean(AppriseClient::class)
    fun appriseClient(): AppriseClient = mockk()
}
