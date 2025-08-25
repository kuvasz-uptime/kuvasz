package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
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
class SettingsControllerV2Test(settingsClient: SettingsClientV2, appGlobals: AppGlobals) : DatabaseBehaviorSpec({

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
                result.app.editabilityState.areHttpMonitorsReadOnly shouldBe true

                with(result.smtp.shouldNotBeNull()) {
                    host shouldBe "localhost"
                    port shouldBeGreaterThan 0
                    transportStrategy shouldBe "SMTP"
                }

                with(result.metricsExport) {
                    exportEnabled shouldBe true
                    meters.sslExpiry shouldBe true
                    meters.httpLatestLatency shouldBe true
                    meters.httpUptimeStatus shouldBe true
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
