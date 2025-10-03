package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.metrics.HttpExporterTest
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class SSLStatusExporterTest : HttpExporterTest("enabled-metrics-ssl-status") {

    init {
        given("an enabled SSL status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                // Enabled monitor without status
                createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    url = "https://test.enabled.no-status",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(20)),
                        null
                    )
                )
                sslEventRepository().insertFromMonitorEvent(
                    SSLInvalidEvent(
                        monitor = disabledMonitorWithStatus,
                        SSLValidationError("irrelevant"),
                        null,
                    )
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.http.ssl.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveTargetTag enabledMonitorWithStatus.url
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val enabledMonitorWithoutStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    url = "https://test.enabled.no-status",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )

                val firstMonitorPreviousEvent = sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(30)),
                        null
                    )
                )
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = disabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(60)),
                        null
                    )
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    SSLInvalidEvent(
                        enabledMonitorWithStatus,
                        SSLValidationError("irrelevant"),
                        firstMonitorPreviousEvent
                    )
                )
                eventDispatcher().dispatch(
                    SSLWillExpireEvent(
                        enabledMonitorWithoutStatus,
                        CertificateInfo(validTo = OffsetDateTime.now().plusDays(60).plusDays(4)),
                        null,
                    )
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the enabled monitor with status should be updated
                    registeredMeters.forOne { withPreviousStatus ->
                        withPreviousStatus shouldHaveNameTag enabledMonitorWithStatus.name
                        withPreviousStatus shouldHaveValue 0.0 // The status is invalid, so the value should be 0
                    }
                    // The meter for the enabled monitor without status should be created
                    registeredMeters.forOne { withoutPreviousStatus ->
                        withoutPreviousStatus shouldHaveNameTag enabledMonitorWithoutStatus.name
                        withoutPreviousStatus shouldHaveValue 1.0 // The status is valid, so the value should be 1
                    }
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val anotherEnabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    url = "https://test.enabled.other",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    url = "https://yet.another.enabled",
                    enabled = true,
                    sslCheckEnabled = true,
                )
                val disabledMonitorWithStatus = createHttpMonitor(
                    httpMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = true,
                    sslCheckEnabled = false,
                )

                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = enabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(30)),
                        null
                    )
                )
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = anotherEnabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(60)),
                        null
                    )
                )

                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = yetAnotherEnabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(90)),
                        null
                    )
                )
                sslEventRepository().insertFromMonitorEvent(
                    SSLValidEvent(
                        monitor = disabledMonitorWithStatus,
                        certInfo = CertificateInfo(validTo = OffsetDateTime.now().plusDays(50)),
                        null
                    )
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                httpMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                httpMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                httpMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorSSLEnableUpdate)
                httpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the disabled monitor should be removed
                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithStatus.name }
                    // The deleted monitor's meter should not exist
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithStatus.name }
                    // The meter for the enabled monitor should be updated with the new name
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 1.0
                    }
                    // The meter for the newly enabled monitor should be created
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithStatus.name
                        newlyEnabledMonitor shouldHaveValue 1.0
                    }
                }
            }
        }
    }
}
