package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.MsTeamsCardFactory
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsNotificationConfig
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class MsTeamsWebhookServiceTest(
    private val client: MsTeamsWebhookClient,
    private val cardFactory: MsTeamsCardFactory,
    private val msTeamsWebhookService: MsTeamsWebhookService,
) : ShouldSpec({

    val testWebhookUrl = "https://prod-11.westeurope.logic.azure.com:443/workflows/aaa/triggers/manual/paths/invoke"

    fun config() = mockk<MsTeamsNotificationConfig>(relaxed = true) {
        every { webhookUrl } returns testWebhookUrl
    }

    context("sendTestMessage") {

        should("call the webhook client with the test card") {
            val expectedMessage = cardFactory.testMessage()
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) } returns Single.just("OK")

            val result = msTeamsWebhookService.sendTestMessage(config()).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()

            verify(exactly = 1) { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) }
        }

        should("return a failed result when the client call fails") {
            val expectedMessage = cardFactory.testMessage()
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = msTeamsWebhookService.sendTestMessage(config()).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")

            verify(exactly = 4) { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) }
        }
    }

    context("sendEvent") {

        should("build the card for the given event and post it to the configured URL") {
            val monitor = HttpMonitorRecord()
                .setId(1111)
                .setName("test_monitor")
                .setUrl("https://test.url")
                .setSensitiveUrl(false)
            val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 300, null)
            val expectedMessage = cardFactory.fromUptimeEvent(event)
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) } returns Single.just("OK")

            msTeamsWebhookService.sendEvent(config(), event).blockingGet() shouldBe "OK"

            verify(exactly = 1) { mockClient.sendMessage(testWebhookUrl.toUri(), expectedMessage) }
        }
    }
}) {
    @MockBean(MsTeamsWebhookClient::class)
    fun msTeamsWebhookClient(): MsTeamsWebhookClient = mockk()
}
