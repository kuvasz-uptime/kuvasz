package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.dto.IntegrationValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.type
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.exceptions.BeanInstantiationException

@SMTPTest
class IntegrationBootstrappingTest : StringSpec({

    "IntegrationRepository should load the integrations from YAML upon startup if they are all valid" {
        val ctx = testAppContext("full-integrations-setup")

        with(ctx.getBean<IntegrationRepository>()) {
            configuredIntegrations shouldHaveSize 18
            enabledIntegrations shouldHaveSize 12
            enabledIntegrationsByType shouldHaveSize 6
            globallyEnabledIntegrationsByType shouldHaveSize 6

            // Check that all integrations are loaded correctly
            with(configuredIntegrations) {
                // Slack
                forOne { implicitlyEnabledSlack ->
                    implicitlyEnabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
                    val config = implicitlyEnabledSlack.value as SlackNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXX"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledSlack ->
                    globallyEnabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, "Global2")
                    val config = globallyEnabledSlack.value as SlackNotificationConfig
                    config.name shouldBe "Global2"
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/YYYYYYYYYY"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                forOne { disabledSlack ->
                    disabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, "disabled")
                    val config = disabledSlack.value as SlackNotificationConfig
                    config.name shouldBe "disabled"
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/ZZZZZZZZZZ"
                    config.enabled shouldBe false
                    config.global shouldBe false
                }
                // Email
                forOne { implicitlyEnabledEmail ->
                    implicitlyEnabledEmail.key shouldBe IntegrationID(IntegrationType.EMAIL, "test_implicitly_enabled")
                    val config = implicitlyEnabledEmail.value as EmailNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.EMAIL
                    config.fromAddress shouldBe "noreply@other.dev"
                    config.toAddress shouldBe "foo@bar.com"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledEmail ->
                    globallyEnabledEmail.key shouldBe IntegrationID(IntegrationType.EMAIL, "Global-343")
                    val config = globallyEnabledEmail.value as EmailNotificationConfig
                    config.name shouldBe "Global-343"
                    config.type shouldBe IntegrationType.EMAIL
                    config.fromAddress shouldBe "foo@bar.com"
                    config.toAddress shouldBe "blabla@example.com"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                forOne { disabledEmail ->
                    disabledEmail.key shouldBe IntegrationID(IntegrationType.EMAIL, "disabled")
                    val config = disabledEmail.value as EmailNotificationConfig
                    config.name shouldBe "disabled"
                    config.type shouldBe IntegrationType.EMAIL
                    config.fromAddress shouldBe "jkfds@jklfds.com"
                    config.toAddress shouldBe "irrelevant@jfdalk.com"
                    config.enabled shouldBe false
                    config.global shouldBe false
                }
                // PagerDuty
                forOne { implicitlyEnabledPagerduty ->
                    implicitlyEnabledPagerduty.key shouldBe IntegrationID(
                        IntegrationType.PAGERDUTY,
                        "test_implicitly_enabled"
                    )
                    val config = implicitlyEnabledPagerduty.value as PagerdutyConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.PAGERDUTY
                    config.integrationKey shouldBe "1234567890abcdef1234567890abcdef"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledPagerduty ->
                    globallyEnabledPagerduty.key shouldBe IntegrationID(IntegrationType.PAGERDUTY, "global")
                    val config = globallyEnabledPagerduty.value as PagerdutyConfig
                    config.name shouldBe "global"
                    config.type shouldBe IntegrationType.PAGERDUTY
                    config.integrationKey shouldBe "abcdef1234567890abcdef1234567890"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                forOne { disabledPagerduty ->
                    disabledPagerduty.key shouldBe IntegrationID(IntegrationType.PAGERDUTY, "disabled")
                    val config = disabledPagerduty.value as PagerdutyConfig
                    config.name shouldBe "disabled"
                    config.type shouldBe IntegrationType.PAGERDUTY
                    config.integrationKey shouldBe "fedcba0987654321fedcba0987654321"
                    config.enabled shouldBe false
                    config.global shouldBe false
                }
                // Telegram
                forOne { implicitlyEnabledTelegram ->
                    implicitlyEnabledTelegram.key shouldBe IntegrationID(
                        IntegrationType.TELEGRAM,
                        "test: implicitly enabled"
                    )
                    val config = implicitlyEnabledTelegram.value as TelegramNotificationConfig
                    config.name shouldBe "test: implicitly enabled"
                    config.type shouldBe IntegrationType.TELEGRAM
                    config.apiToken shouldBe "123456789:ABCdefGhIJKlmnoPQRstuVWXyZ"
                    config.chatId shouldBe "-1001234567890"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledTelegram ->
                    globallyEnabledTelegram.key shouldBe IntegrationID(IntegrationType.TELEGRAM, "global")
                    val config = globallyEnabledTelegram.value as TelegramNotificationConfig
                    config.name shouldBe "global"
                    config.type shouldBe IntegrationType.TELEGRAM
                    config.apiToken shouldBe "ABCdefGhIJKlmnoPQRstuVWXyZ123456789"
                    config.chatId shouldBe "-1000987654321"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                forOne { disabledTelegram ->
                    disabledTelegram.key shouldBe IntegrationID(IntegrationType.TELEGRAM, "disabled")
                    val config = disabledTelegram.value as TelegramNotificationConfig
                    config.name shouldBe "disabled"
                    config.type shouldBe IntegrationType.TELEGRAM
                    config.apiToken shouldBe "0987654321zyxwvutsrqponmlkjihgfedcba"
                    config.chatId shouldBe "-1001122334455"
                    config.enabled shouldBe false
                    config.global shouldBe false
                }
                // Webhook
                forOne { implicitlyEnabledWebhook ->
                    implicitlyEnabledWebhook.key shouldBe IntegrationID(
                        IntegrationType.WEBHOOK,
                        "test_implicitly_enabled"
                    )
                    val config = implicitlyEnabledWebhook.value as WebhookNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.WEBHOOK
                    config.url shouldBe "https://custom-webhook.com/webhook"
                    config.enabled shouldBe true
                    config.global shouldBe false
                    config.payloadTemplate shouldBe
                        "{\"request_id\": \"342342\",\"status\": {% if ctx.type == 'HTTP_UP' %}OK{% else %}" +
                        "{{ctx.type}}{% endif %}}"
                    config.requestHeaders.shouldNotBeNull().shouldBeEmpty()
                    config.excludedEvents.shouldContainExactly(
                        IntegrationEventType.HTTP_UP,
                    )
                }
                forOne { globallyEnabledWebhook ->
                    globallyEnabledWebhook.key shouldBe IntegrationID(IntegrationType.WEBHOOK, "Global2_with_headers")
                    val config = globallyEnabledWebhook.value as WebhookNotificationConfig
                    config.name shouldBe "Global2_with_headers"
                    config.type shouldBe IntegrationType.WEBHOOK
                    config.url shouldBe "https://custom-global-webhook.com"
                    config.enabled shouldBe true
                    config.global shouldBe true
                    config.requestHeaders.shouldNotBeNull() shouldContainAll mapOf(
                        "User-Agent" to "Mozilla/5.0",
                        "X-Custom-Header" to "custom-value",
                    )
                    config.payloadTemplate.shouldBeNull()
                    config.excludedEvents.shouldBeNull()
                }
                forOne { disabledWebhook ->
                    disabledWebhook.key shouldBe IntegrationID(IntegrationType.WEBHOOK, "disabled")
                    val config = disabledWebhook.value as WebhookNotificationConfig
                    config.name shouldBe "disabled"
                    config.type shouldBe IntegrationType.WEBHOOK
                    config.url shouldBe "https://disabled-webhook.com"
                    config.enabled shouldBe false
                    config.global shouldBe false
                    config.requestHeaders.shouldNotBeNull().shouldBeEmpty()
                    config.payloadTemplate.shouldBeNull()
                    config.excludedEvents shouldContainExactlyInAnyOrder listOf(
                        IntegrationEventType.HTTP_UP,
                        IntegrationEventType.PUSH_UP,
                    )
                }
            }

            // Enabled integrations
            with(enabledIntegrations) {
                // Slack
                forOne { implicitlyEnabledSlack ->
                    implicitlyEnabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")
                    val config = implicitlyEnabledSlack.value as SlackNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXX"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledSlack ->
                    globallyEnabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, "Global2")
                    val config = globallyEnabledSlack.value as SlackNotificationConfig
                    config.name shouldBe "Global2"
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/YYYYYYYYYY"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                // Email
                forOne { implicitlyEnabledEmail ->
                    implicitlyEnabledEmail.key shouldBe IntegrationID(IntegrationType.EMAIL, "test_implicitly_enabled")
                    val config = implicitlyEnabledEmail.value as EmailNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.EMAIL
                    config.fromAddress shouldBe "noreply@other.dev"
                    config.toAddress shouldBe "foo@bar.com"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledEmail ->
                    globallyEnabledEmail.key shouldBe IntegrationID(IntegrationType.EMAIL, "Global-343")
                    val config = globallyEnabledEmail.value as EmailNotificationConfig
                    config.name shouldBe "Global-343"
                    config.type shouldBe IntegrationType.EMAIL
                    config.fromAddress shouldBe "foo@bar.com"
                    config.toAddress shouldBe "blabla@example.com"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                // PagerDuty
                forOne { implicitlyEnabledPagerduty ->
                    implicitlyEnabledPagerduty.key shouldBe IntegrationID(
                        IntegrationType.PAGERDUTY,
                        "test_implicitly_enabled"
                    )
                    val config = implicitlyEnabledPagerduty.value as PagerdutyConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.PAGERDUTY
                    config.integrationKey shouldBe "1234567890abcdef1234567890abcdef"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledPagerduty ->
                    globallyEnabledPagerduty.key shouldBe IntegrationID(IntegrationType.PAGERDUTY, "global")
                    val config = globallyEnabledPagerduty.value as PagerdutyConfig
                    config.name shouldBe "global"
                    config.type shouldBe IntegrationType.PAGERDUTY
                    config.integrationKey shouldBe "abcdef1234567890abcdef1234567890"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                // Telegram
                forOne { implicitlyEnabledTelegram ->
                    implicitlyEnabledTelegram.key shouldBe IntegrationID(
                        IntegrationType.TELEGRAM,
                        "test: implicitly enabled"
                    )
                    val config = implicitlyEnabledTelegram.value as TelegramNotificationConfig
                    config.name shouldBe "test: implicitly enabled"
                    config.type shouldBe IntegrationType.TELEGRAM
                    config.apiToken shouldBe "123456789:ABCdefGhIJKlmnoPQRstuVWXyZ"
                    config.chatId shouldBe "-1001234567890"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledTelegram ->
                    globallyEnabledTelegram.key shouldBe IntegrationID(IntegrationType.TELEGRAM, "global")
                    val config = globallyEnabledTelegram.value as TelegramNotificationConfig
                    config.name shouldBe "global"
                    config.type shouldBe IntegrationType.TELEGRAM
                    config.apiToken shouldBe "ABCdefGhIJKlmnoPQRstuVWXyZ123456789"
                    config.chatId shouldBe "-1000987654321"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
                // Webhook
                forOne { implicitlyEnabledWebhook ->
                    implicitlyEnabledWebhook.key shouldBe IntegrationID(
                        IntegrationType.WEBHOOK,
                        "test_implicitly_enabled"
                    )
                    val config = implicitlyEnabledWebhook.value as WebhookNotificationConfig
                    config.name shouldBe "test_implicitly_enabled"
                    config.type shouldBe IntegrationType.WEBHOOK
                    config.url shouldBe "https://custom-webhook.com/webhook"
                    config.enabled shouldBe true
                    config.global shouldBe false
                    config.payloadTemplate shouldBe
                        "{\"request_id\": \"342342\",\"status\": {% if ctx.type == 'HTTP_UP' %}OK{% else %}" +
                        "{{ctx.type}}{% endif %}}"
                    config.requestHeaders.shouldNotBeNull().shouldBeEmpty()
                    config.excludedEvents.shouldContainExactly(
                        IntegrationEventType.HTTP_UP,
                    )
                }
                forOne { globallyEnabledWebhook ->
                    globallyEnabledWebhook.key shouldBe IntegrationID(IntegrationType.WEBHOOK, "Global2_with_headers")
                    val config = globallyEnabledWebhook.value as WebhookNotificationConfig
                    config.name shouldBe "Global2_with_headers"
                    config.type shouldBe IntegrationType.WEBHOOK
                    config.url shouldBe "https://custom-global-webhook.com"
                    config.enabled shouldBe true
                    config.global shouldBe true
                    config.requestHeaders.shouldNotBeNull() shouldContainAll mapOf(
                        "User-Agent" to "Mozilla/5.0",
                        "X-Custom-Header" to "custom-value",
                    )
                    config.payloadTemplate.shouldBeNull()
                    config.excludedEvents.shouldBeNull()
                }
            }

            // Enabled integrations by type
            enabledIntegrationsByType[IntegrationType.SLACK].shouldNotBeNull().shouldHaveSize(2)
            enabledIntegrationsByType[IntegrationType.EMAIL].shouldNotBeNull().shouldHaveSize(2)
            enabledIntegrationsByType[IntegrationType.TELEGRAM].shouldNotBeNull().shouldHaveSize(2)
            enabledIntegrationsByType[IntegrationType.PAGERDUTY].shouldNotBeNull().shouldHaveSize(2)
            enabledIntegrationsByType[IntegrationType.WEBHOOK].shouldNotBeNull().shouldHaveSize(2)

            // Global integrations
            with(globallyEnabledIntegrationsByType[IntegrationType.SLACK]?.single() as SlackNotificationConfig) {
                name shouldBe "Global2"
                enabled shouldBe true
                webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/YYYYYYYYYY"
            }

            with(globallyEnabledIntegrationsByType[IntegrationType.EMAIL]?.single() as EmailNotificationConfig) {
                name shouldBe "Global-343"
                enabled shouldBe true
                fromAddress shouldBe "foo@bar.com"
                toAddress shouldBe "blabla@example.com"
            }

            with(globallyEnabledIntegrationsByType[IntegrationType.TELEGRAM]?.single() as TelegramNotificationConfig) {
                name shouldBe "global"
                enabled shouldBe true
                apiToken shouldBe "ABCdefGhIJKlmnoPQRstuVWXyZ123456789"
                chatId shouldBe "-1000987654321"
            }

            with(globallyEnabledIntegrationsByType[IntegrationType.PAGERDUTY]?.single() as PagerdutyConfig) {
                name shouldBe "global"
                enabled shouldBe true
                integrationKey shouldBe "abcdef1234567890abcdef1234567890"
            }

            with(globallyEnabledIntegrationsByType[IntegrationType.WEBHOOK]?.single() as WebhookNotificationConfig) {
                name shouldBe "Global2_with_headers"
                enabled shouldBe true
                url shouldBe "https://custom-global-webhook.com"
            }
        }
    }

    "app should be able to start if there are no integrations configured" {
        val ctx = testAppContext()

        with(ctx.getBean<IntegrationRepository>()) {
            configuredIntegrations shouldHaveSize 0
            enabledIntegrations shouldHaveSize 0
            enabledIntegrationsByType shouldHaveSize 0
            globallyEnabledIntegrationsByType shouldHaveSize 0
        }
    }

    "app should be able to start with complex integration names" {
        val ctx = shouldNotThrowAny {
            testAppContext("complex-integration-name")
        }

        with(ctx.getBean<IntegrationRepository>()) {
            configuredIntegrations shouldHaveSize 2
            enabledIntegrations shouldHaveSize 2
            enabledIntegrationsByType shouldHaveSize 1
            globallyEnabledIntegrationsByType shouldHaveSize 1

            with(configuredIntegrations) {
                forOne { slackWithComplexName ->
                    val expectedName = "ThisOneContéins!SpecialChara#cters\$AndColons:Spaces Toő"
                    slackWithComplexName.key shouldBe IntegrationID(IntegrationType.SLACK, expectedName)
                    val config = slackWithComplexName.value as SlackNotificationConfig
                    config.name shouldBe expectedName
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXX"
                    config.enabled shouldBe true
                    config.global shouldBe false
                }
                forOne { globallyEnabledSlack ->
                    val expectedName = "Global2"
                    globallyEnabledSlack.key shouldBe IntegrationID(IntegrationType.SLACK, expectedName)
                    val config = globallyEnabledSlack.value as SlackNotificationConfig
                    config.name shouldBe expectedName
                    config.type shouldBe IntegrationType.SLACK
                    config.webhookUrl shouldBe "https://hooks.slack.com/services/T00000000/B00000000/YYYYYYYYYY"
                    config.enabled shouldBe true
                    config.global shouldBe true
                }
            }
        }
    }

    "app should not start if there are integrations with the same name and type" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("duplicate-integrations")
        }

        ex.message shouldContain "Duplicate integration configuration found for pagerduty:test. " +
            "Please ensure each integration has a unique name."
    }

    "app should not start if there is at least one integration with a badly formatted name" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("blank-integration-name")
        }

        ex.message shouldContain "Invalid integration name [ \n" +
            " ]. Integration name must be a non-blank string."
    }

    "app should not start if there is an invalid integration config" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("invalid-integration-config")
        }

        ex.message shouldContain "SlackNotificationConfig.getWebhookUrl - " +
            IntegrationValidationMessages.SLACK_WEBHOOK_URL_NOT_BLANK
    }

    "app should not start if a webhook config contains an invalid header" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("invalid-webhook-header")
        }

        ex.message shouldContain "WebhookNotificationConfig.getRequestHeaders - " +
            ValidationMessages.VALID_HEADER_NAMES
    }

    "app should not start if a webhook config contains an invalid payload template" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("invalid-webhook-template")
        }

        ex.message shouldContain "Failed to parse payload/header template for webhook:Global2_with_headers"
    }

    "app should not start if a webhook config contains an invalid request header template" {
        val ex = shouldThrow<BeanInstantiationException> {
            testAppContext("invalid-webhook-header-template")
        }

        ex.message shouldContain "Failed to parse payload/header template for webhook:Global2_with_headers"
    }
})

class IntegrationBootstrappingWithoutSMTPTest : StringSpec({
    "EmailConfigs should not be loaded into enabledConfigurations if SMTP is not configured" {
        val ctx = testAppContext("full-integrations-setup")

        with(ctx.getBean<IntegrationRepository>()) {
            configuredIntegrations shouldHaveSize 18
            enabledIntegrations shouldHaveSize 10 // Email configs should not be enabled without SMTP config
            enabledIntegrationsByType shouldHaveSize 5 // Email type should not be present
            globallyEnabledIntegrationsByType shouldHaveSize 5 // Email type should not be present

            // Check that Email configs are not loaded as enabled
            val implicitlyEnabledId = IntegrationID(IntegrationType.EMAIL, "test_implicitly_enabled")
            val globallyEnabledId = IntegrationID(IntegrationType.EMAIL, "Global-343")
            configuredIntegrations[implicitlyEnabledId].shouldNotBeNull()
            configuredIntegrations[globallyEnabledId].shouldNotBeNull()
            enabledIntegrations[implicitlyEnabledId].shouldBeNull()
            enabledIntegrations[globallyEnabledId].shouldBeNull()
        }
    }
})
