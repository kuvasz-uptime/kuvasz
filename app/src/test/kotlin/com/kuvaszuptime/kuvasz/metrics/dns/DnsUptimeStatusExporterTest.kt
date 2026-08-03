package com.kuvaszuptime.kuvasz.metrics.dns

import com.kuvaszuptime.kuvasz.metrics.DnsExporterTest
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DnsUptimeStatusExporterTest : DnsExporterTest("enabled-metrics-dns-uptime-status") {

    init {
        given("an enabled DNS uptime status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.dns.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    DnsMonitorDownEvent(
                        enabledMonitorWithStatus,
                        previousEvent = firstMonitorPreviousEvent,
                        error = "timeout",
                    )
                )
                eventDispatcher().dispatch(
                    DnsMonitorUpEvent(enabledMonitorWithoutStatus, previousEvent = null, latencyInMs = 5)
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forOne { withPreviousStatus ->
                        withPreviousStatus shouldHaveNameTag enabledMonitorWithStatus.name
                        withPreviousStatus shouldHaveValue 0.0
                    }
                    registeredMeters.forOne { withoutPreviousStatus ->
                        withoutPreviousStatus shouldHaveNameTag enabledMonitorWithoutStatus.name
                        withoutPreviousStatus shouldHaveValue 1.0
                    }
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(enabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(anotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(yetAnotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dnsUptimeEventRepository().insertFromMonitorEvent(
                    DnsMonitorUpEvent(disabledMonitorWithStatus, null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                dnsMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                dnsMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                dnsMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                dnsMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithStatus.name }
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithStatus.name }
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 1.0
                    }
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithStatus.name
                        newlyEnabledMonitor shouldHaveValue 1.0
                    }
                }
            }
        }
    }
}
