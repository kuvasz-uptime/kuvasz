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

class DnsLatencyExporterTest : DnsExporterTest("enabled-metrics-dns-latency") {

    init {
        given("an enabled DNS latency exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without latency records
                createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100)
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20)
                dnsMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with latency") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.dns.latency.latest.milliseconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithLatency.name
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 90)
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20)
                dnsMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    DnsMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = 30)
                )
                eventDispatcher().dispatch(
                    DnsMonitorUpEvent(enabledMonitorWithoutLatency, null, latencyInMs = 40)
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forOne { withPreviousLatency ->
                        withPreviousLatency shouldHaveNameTag enabledMonitorWithLatency.name
                        withPreviousLatency shouldHaveValue 30.0
                    }
                    registeredMeters.forOne { withoutPreviousLatency ->
                        withoutPreviousLatency shouldHaveNameTag enabledMonitorWithoutLatency.name
                        withoutPreviousLatency shouldHaveValue 40.0
                    }
                }
            }

            `when`("there is an up event with null latency") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DnsMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("there is a down event carrying a latency (latency threshold breached)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DnsMonitorDownEvent(
                        enabledMonitorWithLatency,
                        error = "too slow",
                        previousEvent = null,
                        latencyInMs = 120,
                    )
                )

                then("it should update the existing meter with the measured latency") {
                    meterRegistry().meters.single() shouldHaveValue 120.0
                }
            }

            `when`("there is a down event without a latency (resolution failed)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DnsMonitorDownEvent(enabledMonitorWithLatency, error = "timed out", previousEvent = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDnsMonitor(
                    dnsMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dnsMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100)
                dnsMetricsLogRepository().insertLog(anotherEnabledMonitorWithLatency.id, latencyMs = 20)
                dnsMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)
                dnsMetricsLogRepository().insertLog(yetAnotherEnabledMonitorWithLatency.id, latencyMs = 10)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                dnsMonitorActions().updateMonitor(enabledMonitorWithLatency.id, monitorDisableUpdate)
                dnsMonitorActions().updateMonitor(anotherEnabledMonitorWithLatency.id, monitorNameUpdate)
                dnsMonitorActions().updateMonitor(disabledMonitorWithLatency.id, monitorEnableUpdate)
                dnsMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithLatency.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithLatency.name }
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithLatency.name }
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 20.0
                    }
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithLatency.name
                        newlyEnabledMonitor shouldHaveValue 50.0
                    }
                }
            }
        }
    }
}
