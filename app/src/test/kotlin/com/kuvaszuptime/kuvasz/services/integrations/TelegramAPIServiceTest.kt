package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.TelegramAPIMessage
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class TelegramAPIServiceTest(
    private val client: TelegramAPIClient,
    private val telegramAPIService: TelegramAPIService,
) : ShouldSpec({

    context("sendTestMessage") {

        should("call the API to send the message") {
            val testChatId = "-34233434"
            val testAPIToken = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11"
            val expectedMessage = TelegramAPIMessage(chatId = testChatId, text = Messages.integrationTestMessage())
            val config = mockk<TelegramNotificationConfig>(relaxed = true) {
                every { chatId } returns testChatId
                every { apiToken } returns testAPIToken
            }
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testAPIToken, expectedMessage) } returns Single.just("ok")

            val result = telegramAPIService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verify(exactly = 1) { mockClient.sendMessage(testAPIToken, expectedMessage) }
        }

        should("return a failed result when the API call fails") {
            val testChatId = "-34233434"
            val testAPIToken = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11"
            val expectedMessage = TelegramAPIMessage(chatId = testChatId, text = Messages.integrationTestMessage())
            val config = mockk<TelegramNotificationConfig>(relaxed = true) {
                every { chatId } returns testChatId
                every { apiToken } returns testAPIToken
            }
            val mockClient = getMock(client)
            every { mockClient.sendMessage(testAPIToken, expectedMessage) } returns Single.error(
                RuntimeException("Something went wrong")
            )

            val result = telegramAPIService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe Messages.failedTestResultMessage("Something went wrong")
            verify(exactly = 1) { mockClient.sendMessage(testAPIToken, expectedMessage) }
        }
    }
}) {
    @MockBean(TelegramAPIClient::class)
    fun telegramAPIClient(): TelegramAPIClient = mockk()
}
