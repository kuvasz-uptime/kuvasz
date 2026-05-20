package com.kuvaszuptime.kuvasz.metrics.icmp

import com.kuvaszuptime.kuvasz.metrics.IcmpExporterTest
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class IcmpLatencyExporterTest : IcmpExporterTest("enabled-metrics-icmp-latency") {

    init {
        given("an enabled ICMP latency exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without latency records
                createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 100,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 20,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitorWithLatency.id,
                    latencyMs = 50,
                    packetLossPercentage = 0,
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with latency") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.icmp.latency.latest.milliseconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithLatency.name
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 90,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 20,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitorWithLatency.id,
                    latencyMs = 50,
                    packetLossPercentage = 0,
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = 30, packetLossPercentage = 0)
                )
                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(enabledMonitorWithoutLatency, null, latencyInMs = 40, packetLossPercentage = 0)
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

                val enabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 50,
                    packetLossPercentage = 0,
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = null, packetLossPercentage = 0)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithLatency.id,
                    latencyMs = 100,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    anotherEnabledMonitorWithLatency.id,
                    latencyMs = 20,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitorWithLatency.id,
                    latencyMs = 50,
                    packetLossPercentage = 0,
                )
                icmpMetricsLogRepository().insertLog(
                    yetAnotherEnabledMonitorWithLatency.id,
                    latencyMs = 10,
                    packetLossPercentage = 0,
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                icmpMonitorActions().updateMonitor(enabledMonitorWithLatency.id, monitorDisableUpdate)
                icmpMonitorActions().updateMonitor(anotherEnabledMonitorWithLatency.id, monitorNameUpdate)
                icmpMonitorActions().updateMonitor(disabledMonitorWithLatency.id, monitorEnableUpdate)
                icmpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithLatency.id)

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
