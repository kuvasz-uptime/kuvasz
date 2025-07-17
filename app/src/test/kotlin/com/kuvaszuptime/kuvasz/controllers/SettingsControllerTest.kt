package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(
    environments = [
        "full-integrations-setup",
        "yaml-monitors",
        "enabled-metrics-otlp",
        "enabled-metrics-prometheus",
    ]
)
@SMTPTest
@Property(name = "micronaut.security.token.generator.access-token.expiration", value = "3600")
@Property(name = "app-config.event-data-retention-days", value = "5")
@Property(name = "app-config.latency-data-retention-days", value = "6")
@Property(name = "app-config.language", value = "en")
@Property(name = "app-config.log-event-handler", value = "true")
class SettingsControllerTest(settingsClient: SettingsClient, appGlobals: AppGlobals) : DatabaseBehaviorSpec({

    given("the SettingsController") {

        `when`("the getSettings method is called") {

            val result = settingsClient.getSettings()

            then("it should return the settings") {
                result.authentication.enabled shouldBe false
                result.authentication.accessTokenMaxAge shouldBe 3600L
                result.app.eventDataRetentionDays shouldBe 5
                result.app.latencyDataRetentionDays shouldBe 6
                result.app.language shouldBe "en"
                result.app.eventLoggingEnabled shouldBe true
                result.app.version.shouldNotBeEmpty() shouldBe appGlobals.appVersion
                result.app.readOnlyMode shouldBe true

                with(result.integrations) {
                    with(slack.shouldHaveSize(3)) {
                        forOne { implicitlyEnabled ->
                            implicitlyEnabled.id shouldBe IntegrationID(
                                IntegrationType.SLACK,
                                "test_implicitly_enabled"
                            )
                            implicitlyEnabled.name shouldBe "test_implicitly_enabled"
                            implicitlyEnabled.enabled shouldBe true
                            implicitlyEnabled.global shouldBe false
                        }
                        forOne { global ->
                            global.id shouldBe IntegrationID(IntegrationType.SLACK, "Global2")
                            global.name shouldBe "Global2"
                            global.enabled shouldBe true
                            global.global shouldBe true
                        }
                        forOne { disabled ->
                            disabled.id shouldBe IntegrationID(IntegrationType.SLACK, "disabled")
                            disabled.name shouldBe "disabled"
                            disabled.enabled shouldBe false
                            disabled.global shouldBe false
                        }
                    }

                    with(email.shouldHaveSize(3)) {
                        forOne { implicitlyEnabled ->
                            implicitlyEnabled.id shouldBe IntegrationID(
                                IntegrationType.EMAIL,
                                "test_implicitly_enabled"
                            )
                            implicitlyEnabled.name shouldBe "test_implicitly_enabled"
                            implicitlyEnabled.enabled shouldBe true
                            implicitlyEnabled.global shouldBe false
                            implicitlyEnabled.fromAddress shouldBe "noreply@other.dev"
                            implicitlyEnabled.toAddress shouldBe "foo@bar.com"
                        }
                        forOne { global ->
                            global.id shouldBe IntegrationID(IntegrationType.EMAIL, "Global-343")
                            global.name shouldBe "Global-343"
                            global.enabled shouldBe true
                            global.global shouldBe true
                            global.fromAddress shouldBe "foo@bar.com"
                            global.toAddress shouldBe "blabla@example.com"
                        }
                        forOne { disabled ->
                            disabled.id shouldBe IntegrationID(IntegrationType.EMAIL, "disabled")
                            disabled.name shouldBe "disabled"
                            disabled.enabled shouldBe false
                            disabled.global shouldBe false
                            disabled.fromAddress shouldBe "jkfds@jklfds.com"
                            disabled.toAddress shouldBe "irrelevant@jfdalk.com"
                        }
                    }

                    with(pagerduty.shouldHaveSize(3)) {
                        forOne { implicitlyEnabled ->
                            implicitlyEnabled.id shouldBe IntegrationID(
                                IntegrationType.PAGERDUTY,
                                "test_implicitly_enabled"
                            )
                            implicitlyEnabled.name shouldBe "test_implicitly_enabled"
                            implicitlyEnabled.enabled shouldBe true
                            implicitlyEnabled.global shouldBe false
                        }
                        forOne { global ->
                            global.id shouldBe IntegrationID(IntegrationType.PAGERDUTY, "global")
                            global.name shouldBe "global"
                            global.enabled shouldBe true
                            global.global shouldBe true
                        }
                        forOne { disabled ->
                            disabled.id shouldBe IntegrationID(IntegrationType.PAGERDUTY, "disabled")
                            disabled.name shouldBe "disabled"
                            disabled.enabled shouldBe false
                            disabled.global shouldBe false
                        }
                    }

                    with(telegram.shouldHaveSize(3)) {
                        forOne { implicitlyEnabled ->
                            implicitlyEnabled.id shouldBe IntegrationID(
                                IntegrationType.TELEGRAM,
                                "test_implicitly_enabled"
                            )
                            implicitlyEnabled.name shouldBe "test_implicitly_enabled"
                            implicitlyEnabled.enabled shouldBe true
                            implicitlyEnabled.global shouldBe false
                            implicitlyEnabled.chatId shouldBe "-1001234567890"
                        }
                        forOne { global ->
                            global.id shouldBe IntegrationID(IntegrationType.TELEGRAM, "global")
                            global.name shouldBe "global"
                            global.enabled shouldBe true
                            global.global shouldBe true
                            global.chatId shouldBe "-1000987654321"
                        }
                        forOne { disabled ->
                            disabled.id shouldBe IntegrationID(IntegrationType.TELEGRAM, "disabled")
                            disabled.name shouldBe "disabled"
                            disabled.enabled shouldBe false
                            disabled.global shouldBe false
                            disabled.chatId shouldBe "-1001122334455"
                        }
                    }
                    with(smtp.shouldNotBeNull()) {
                        host shouldBe "localhost"
                        port shouldBeGreaterThan 0
                        transportStrategy shouldBe "SMTP"
                    }
                }

                with(result.metricsExport) {
                    exportEnabled shouldBe true
                    meters.sslExpiry shouldBe true
                    meters.latestLatency shouldBe true
                    meters.uptimeStatus shouldBe true
                    meters.sslStatus shouldBe true

                    with(exporters.prometheus) {
                        enabled shouldBe true
                        descriptions shouldBe true
                    }
                    with(exporters.openTelemetry) {
                        enabled shouldBe true
                        url shouldBe "http://otel-collector.example:4317"
                        step shouldBe "PT30M"
                    }
                }
            }
        }
    }
})
