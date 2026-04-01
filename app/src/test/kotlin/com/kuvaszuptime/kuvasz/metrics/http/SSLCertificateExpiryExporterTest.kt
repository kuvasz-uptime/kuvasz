package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.metrics.HttpExporterTest
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class SSLCertificateExpiryExporterTest : HttpExporterTest("enabled-metrics-ssl-expiry") {

    init {
        given("an enabled SSL expiry exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                // Enabled monitor without expiry data
                createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-no-expiry",
                    url = "https://test.enabled.no-expiry",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )
                val firstExpiry = OffsetDateTime.now().plusDays(30)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = firstExpiry),
                        null
                    )
                )
                val secondExpiry = OffsetDateTime.now().plusDays(60)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = disabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = secondExpiry),
                        null
                    )
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with expiry") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.http.ssl.expiry.seconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithExpiry.name
                    expectedMeter shouldHaveTargetTag enabledMonitorWithExpiry.url
                    expectedMeter shouldHaveValue firstExpiry.toEpochSecond().toDouble()
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val enabledMonitorWithoutExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-no-expiry",
                    url = "https://test.enabled.no-expiry",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )

                val firstExpiry = OffsetDateTime.now().plusDays(30)
                val firstMonitorPreviousEvent = sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = firstExpiry),
                        null
                    )
                )
                val secondExpiry = OffsetDateTime.now().plusDays(60)
                val secondMonitorPreviousEvent = sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = disabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = secondExpiry),
                        null
                    )
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    SSLWillExpireEvent(
                        enabledMonitorWithExpiry,
                        CertificateInfo(validTo = firstExpiry.plusDays(2)),
                        firstMonitorPreviousEvent
                    )
                )
                eventDispatcher().dispatch(
                    SSLValidEvent(
                        enabledMonitorWithoutExpiry,
                        CertificateInfo(validTo = secondExpiry.plusDays(4)),
                        secondMonitorPreviousEvent
                    )
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the enabled monitor with expiry should be updated
                    registeredMeters.forOne { withPreviousExpiry ->
                        withPreviousExpiry shouldHaveNameTag enabledMonitorWithExpiry.name
                        withPreviousExpiry shouldHaveValue firstExpiry.plusDays(2).toEpochSecond().toDouble()
                    }
                    // The meter for the enabled monitor without expiry should be created
                    registeredMeters.forOne { withoutPreviousExpiry ->
                        withoutPreviousExpiry shouldHaveNameTag enabledMonitorWithoutExpiry.name
                        withoutPreviousExpiry shouldHaveValue secondExpiry.plusDays(4).toEpochSecond().toDouble()
                    }
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val anotherEnabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    url = "https://test.enabled.other",
                    enabled = true,
                    sslCheckEnabled = true,
                    sensitiveUrl = true,
                )
                val yetAnotherEnabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    url = "https://yet.another.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithExpiry = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )

                val firstExpiry = OffsetDateTime.now().plusDays(30)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = firstExpiry),
                        null
                    )
                )
                val secondExpiry = OffsetDateTime.now().plusDays(60)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = anotherEnabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = secondExpiry),
                        null
                    )
                )

                val thirdExpiry = OffsetDateTime.now().plusDays(90)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = yetAnotherEnabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = thirdExpiry),
                        null
                    )
                )
                val fourthExpiry = OffsetDateTime.now().plusDays(50)
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = disabledMonitorWithExpiry,
                        certInfo = CertificateInfo(validTo = fourthExpiry),
                        null
                    )
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                httpMonitorActions().updateMonitor(enabledMonitorWithExpiry.id, monitorDisableUpdate)
                httpMonitorActions().updateMonitor(anotherEnabledMonitorWithExpiry.id, monitorNameUpdate)
                httpMonitorActions().updateMonitor(disabledMonitorWithExpiry.id, monitorSSLEnableUpdate)
                httpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithExpiry.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the disabled monitor should be removed
                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithExpiry.name }
                    // The deleted monitor's meter should not exist
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithExpiry.name }
                    // The meter for the enabled monitor should be updated with the new name
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue secondExpiry.toEpochSecond().toDouble()
                        updatedMonitor shouldHaveTargetTag "MASKED URL"
                    }
                    // The meter for the newly enabled monitor should be created
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithExpiry.name
                        newlyEnabledMonitor shouldHaveValue fourthExpiry.toEpochSecond().toDouble()
                    }
                }
            }
        }
    }
}
