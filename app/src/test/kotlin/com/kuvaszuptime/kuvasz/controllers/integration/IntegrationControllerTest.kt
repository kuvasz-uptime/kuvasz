package com.kuvaszuptime.kuvasz.controllers.integration

import com.kuvaszuptime.kuvasz.models.dto.integration.EmailNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.PagerdutyConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.SlackNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.TelegramNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.WebhookNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.services.integrations.DiscordWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.EmailTestService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.NotificationTestResult
import com.kuvaszuptime.kuvasz.services.integrations.PagerdutyTestService
import com.kuvaszuptime.kuvasz.services.integrations.SlackWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.TelegramAPIService
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(environments = ["full-integrations-setup"])
@SMTPTest
class IntegrationControllerTest(
    private val integrationClient: IntegrationClient,
    private val slackWebhookService: SlackWebhookService,
    private val emailTestService: EmailTestService,
    private val pagerdutyTestService: PagerdutyTestService,
    private val telegramAPIService: TelegramAPIService,
    private val discordWebhookService: DiscordWebhookService,
    private val integrationRepository: IntegrationRepository,
) : ShouldSpec({

    context("the getIntegrations endpoint") {
        should("return all the configured integrations, ordered by their names") {
            val response = integrationClient.getIntegrations()

            response shouldHaveSize 18
            response.shouldBeSortedBy { it.name }

            response.forOne { implicitlyEnabledSlack ->
                implicitlyEnabledSlack.shouldBeInstanceOf<SlackNotificationConfigDto>()
                implicitlyEnabledSlack.id shouldBe IntegrationID(
                    IntegrationType.SLACK,
                    "test_implicitly_enabled"
                )
                implicitlyEnabledSlack.name shouldBe "test_implicitly_enabled"
                implicitlyEnabledSlack.enabled shouldBe true
                implicitlyEnabledSlack.global shouldBe false
            }
            response.forOne { globalSlack ->
                globalSlack.shouldBeInstanceOf<SlackNotificationConfigDto>()
                globalSlack.id shouldBe IntegrationID(IntegrationType.SLACK, "Global2")
                globalSlack.name shouldBe "Global2"
                globalSlack.enabled shouldBe true
                globalSlack.global shouldBe true
            }
            response.forOne { disabledSlack ->
                disabledSlack.shouldBeInstanceOf<SlackNotificationConfigDto>()
                disabledSlack.id shouldBe IntegrationID(IntegrationType.SLACK, "disabled")
                disabledSlack.name shouldBe "disabled"
                disabledSlack.enabled shouldBe false
                disabledSlack.global shouldBe false
            }

            response.forOne { implicitlyEnabledEmail ->
                implicitlyEnabledEmail.shouldBeInstanceOf<EmailNotificationConfigDto>()
                implicitlyEnabledEmail.id shouldBe IntegrationID(
                    IntegrationType.EMAIL,
                    "test_implicitly_enabled"
                )
                implicitlyEnabledEmail.name shouldBe "test_implicitly_enabled"
                implicitlyEnabledEmail.enabled shouldBe true
                implicitlyEnabledEmail.global shouldBe false
                implicitlyEnabledEmail.fromAddress shouldBe "noreply@other.dev"
                implicitlyEnabledEmail.toAddress shouldBe "foo@bar.com"
            }
            response.forOne { globalEmail ->
                globalEmail.shouldBeInstanceOf<EmailNotificationConfigDto>()
                globalEmail.id shouldBe IntegrationID(IntegrationType.EMAIL, "Global-343")
                globalEmail.name shouldBe "Global-343"
                globalEmail.enabled shouldBe true
                globalEmail.global shouldBe true
                globalEmail.fromAddress shouldBe "foo@bar.com"
                globalEmail.toAddress shouldBe "blabla@example.com"
            }
            response.forOne { disabledEmail ->
                disabledEmail.shouldBeInstanceOf<EmailNotificationConfigDto>()
                disabledEmail.id shouldBe IntegrationID(IntegrationType.EMAIL, "disabled")
                disabledEmail.name shouldBe "disabled"
                disabledEmail.enabled shouldBe false
                disabledEmail.global shouldBe false
                disabledEmail.fromAddress shouldBe "jkfds@jklfds.com"
                disabledEmail.toAddress shouldBe "irrelevant@jfdalk.com"
            }

            response.forOne { implicitlyEnabledPd ->
                implicitlyEnabledPd.shouldBeInstanceOf<PagerdutyConfigDto>()
                implicitlyEnabledPd.id shouldBe IntegrationID(
                    IntegrationType.PAGERDUTY,
                    "test_implicitly_enabled"
                )
                implicitlyEnabledPd.name shouldBe "test_implicitly_enabled"
                implicitlyEnabledPd.enabled shouldBe true
                implicitlyEnabledPd.global shouldBe false
            }
            response.forOne { globalPd ->
                globalPd.shouldBeInstanceOf<PagerdutyConfigDto>()
                globalPd.id shouldBe IntegrationID(IntegrationType.PAGERDUTY, "global")
                globalPd.name shouldBe "global"
                globalPd.enabled shouldBe true
                globalPd.global shouldBe true
            }
            response.forOne { disabledPd ->
                disabledPd.shouldBeInstanceOf<PagerdutyConfigDto>()
                disabledPd.id shouldBe IntegrationID(IntegrationType.PAGERDUTY, "disabled")
                disabledPd.name shouldBe "disabled"
                disabledPd.enabled shouldBe false
                disabledPd.global shouldBe false
            }

            response.forOne { implicitlyEnabledTelegram ->
                implicitlyEnabledTelegram.shouldBeInstanceOf<TelegramNotificationConfigDto>()
                implicitlyEnabledTelegram.id shouldBe IntegrationID(
                    IntegrationType.TELEGRAM,
                    "test: implicitly enabled"
                )
                implicitlyEnabledTelegram.name shouldBe "test: implicitly enabled"
                implicitlyEnabledTelegram.enabled shouldBe true
                implicitlyEnabledTelegram.global shouldBe false
                implicitlyEnabledTelegram.chatId shouldBe "-1001234567890"
            }
            response.forOne { globalTelegram ->
                globalTelegram.shouldBeInstanceOf<TelegramNotificationConfigDto>()
                globalTelegram.id shouldBe IntegrationID(IntegrationType.TELEGRAM, "global")
                globalTelegram.name shouldBe "global"
                globalTelegram.enabled shouldBe true
                globalTelegram.global shouldBe true
                globalTelegram.chatId shouldBe "-1000987654321"
            }
            response.forOne { disabledTelegram ->
                disabledTelegram.shouldBeInstanceOf<TelegramNotificationConfigDto>()
                disabledTelegram.id shouldBe IntegrationID(IntegrationType.TELEGRAM, "disabled")
                disabledTelegram.name shouldBe "disabled"
                disabledTelegram.enabled shouldBe false
                disabledTelegram.global shouldBe false
                disabledTelegram.chatId shouldBe "-1001122334455"
            }

            response.forOne { implicitlyEnabledWebhook ->
                implicitlyEnabledWebhook.shouldBeInstanceOf<WebhookNotificationConfigDto>()
                implicitlyEnabledWebhook.id shouldBe IntegrationID(
                    IntegrationType.WEBHOOK,
                    "test_implicitly_enabled"
                )
                implicitlyEnabledWebhook.name shouldBe "test_implicitly_enabled"
                implicitlyEnabledWebhook.enabled shouldBe true
                implicitlyEnabledWebhook.global shouldBe false
                implicitlyEnabledWebhook.url shouldBe "https://custom-webhook.com/webhook"
                implicitlyEnabledWebhook.payloadTemplate shouldBe
                    "{\"request_id\": \"342342\",\"status\": {% if ctx.type == 'HTTP_UP' %}OK{% else %}" +
                    "{{ctx.type}}{% endif %}}"
            }
            response.forOne { globalWebhook ->
                globalWebhook.shouldBeInstanceOf<WebhookNotificationConfigDto>()
                globalWebhook.id shouldBe IntegrationID(IntegrationType.WEBHOOK, "Global2_with_headers")
                globalWebhook.name shouldBe "Global2_with_headers"
                globalWebhook.enabled shouldBe true
                globalWebhook.global shouldBe true
                globalWebhook.url shouldBe "https://custom-global-webhook.com"
                globalWebhook.payloadTemplate.shouldBeNull()
                globalWebhook.requestHeaders shouldContainAll mapOf(
                    "User-Agent" to "Mozilla/5.0",
                    "X-Custom-Header" to "custom-value",
                )
            }
            response.forOne { disabledWebhook ->
                disabledWebhook.shouldBeInstanceOf<WebhookNotificationConfigDto>()
                disabledWebhook.id shouldBe IntegrationID(IntegrationType.WEBHOOK, "disabled")
                disabledWebhook.name shouldBe "disabled"
                disabledWebhook.enabled shouldBe false
                disabledWebhook.global shouldBe false
                disabledWebhook.url shouldBe "https://disabled-webhook.com"
                disabledWebhook.payloadTemplate.shouldBeNull()
                disabledWebhook.excludedEvents shouldContainExactlyInAnyOrder listOf(
                    IntegrationEventType.HTTP_UP,
                    IntegrationEventType.PUSH_UP,
                )
            }
        }
    }
    context("the sendTestNotification endpoint") {

        fun successResponse(integrationId: IntegrationID) = Single.just(
            NotificationTestResult(
                success = true,
                message = "OK: $integrationId"
            )
        )

        fun integrationConfig(integrationID: IntegrationID) =
            integrationRepository.configuredIntegrations[integrationID].shouldNotBeNull()

        should("return an error when the integration ID is not found") {
            val response = integrationClient.sendTestNotification(
                IntegrationID(
                    IntegrationType.SLACK,
                    "non_existent_integration"
                )
            ).blockingGet()

            response.success shouldBe false
            response.message shouldBe "Integration with ID \"slack:non_existent_integration\" not found"
        }

        should("return success when the integration ID is found (even if it's disabled) - Slack") {
            val integrationId = IntegrationID(IntegrationType.SLACK, "disabled")
            val mockedService = getMock(slackWebhookService)
            every { mockedService.sendTestMessage(any()) } returns successResponse(integrationId)
            val response = integrationClient.sendTestNotification(integrationId).blockingGet()

            response.success shouldBe true
            response.message shouldBe "OK: $integrationId"
            verify(exactly = 1) {
                mockedService.sendTestMessage(integrationConfig(integrationId) as SlackNotificationConfig)
            }
        }

        should("return success when the integration ID is found (even if it's disabled) - Email") {
            val integrationId = IntegrationID(IntegrationType.EMAIL, "disabled")
            val mockedService = getMock(emailTestService)
            every { mockedService.sendTestMessage(any()) } returns successResponse(integrationId)
            val response = integrationClient.sendTestNotification(integrationId).blockingGet()

            response.success shouldBe true
            response.message shouldBe "OK: $integrationId"
            verify(exactly = 1) {
                mockedService.sendTestMessage(integrationConfig(integrationId) as EmailNotificationConfig)
            }
        }

        should("return success when the integration ID is found (even if it's disabled) - PagerDuty") {
            val integrationId = IntegrationID(IntegrationType.PAGERDUTY, "disabled")
            val mockedService = getMock(pagerdutyTestService)
            every { mockedService.sendTestMessage(any()) } returns successResponse(integrationId)
            val response = integrationClient.sendTestNotification(integrationId).blockingGet()

            response.success shouldBe true
            response.message shouldBe "OK: $integrationId"
            verify(exactly = 1) {
                mockedService.sendTestMessage(integrationConfig(integrationId) as PagerdutyConfig)
            }
        }

        should("return success when the integration ID is found (even if it's disabled) - Telegram") {
            val integrationId = IntegrationID(IntegrationType.TELEGRAM, "disabled")
            val mockedService = getMock(telegramAPIService)
            every { mockedService.sendTestMessage(any()) } returns successResponse(integrationId)
            val response = integrationClient.sendTestNotification(integrationId).blockingGet()

            response.success shouldBe true
            response.message shouldBe "OK: $integrationId"
            verify(exactly = 1) {
                mockedService.sendTestMessage(integrationConfig(integrationId) as TelegramNotificationConfig)
            }
        }

        should("return success when the integration ID is found (even if it's disabled) - Discord") {
            val integrationId = IntegrationID(IntegrationType.DISCORD, "disabled")
            val mockedService = getMock(discordWebhookService)
            every { mockedService.sendTestMessage(any()) } returns successResponse(integrationId)
            val response = integrationClient.sendTestNotification(integrationId).blockingGet()

            response.success shouldBe true
            response.message shouldBe "OK: $integrationId"
            verify(exactly = 1) {
                mockedService.sendTestMessage(integrationConfig(integrationId) as DiscordNotificationConfig)
            }
        }
    }
}) {
    @MockBean(SlackWebhookService::class)
    fun mockSlackWebhookService(): SlackWebhookService = mockk()

    @MockBean(EmailTestService::class)
    fun mockEmailTestService(): EmailTestService = mockk()

    @MockBean(PagerdutyTestService::class)
    fun mockPagerdutyTestService(): PagerdutyTestService = mockk()

    @MockBean(TelegramAPIService::class)
    fun mockTelegramAPIService(): TelegramAPIService = mockk()

    @MockBean(DiscordWebhookService::class)
    fun mockDiscordWebhookService(): DiscordWebhookService = mockk()
}
