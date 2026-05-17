package com.kuvaszuptime.kuvasz.metrics.icmp

import com.kuvaszuptime.kuvasz.metrics.IcmpExporterTest
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class IcmpPacketLossExporterTest : IcmpExporterTest("enabled-metrics-icmp-packet-loss") {

    init {
        given("an enabled ICMP packet loss exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithPacketLoss = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without any packet loss records
                createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-data",
                    enabled = true,
                )
                val disabledMonitorWithPacketLoss = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithPacketLoss.id,
                    latencyMs = 10,
                    packetLossPercentage = 50,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithPacketLoss.id,
                    latencyMs = 10,
                    packetLossPercentage = 20,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitorWithPacketLoss.id,
                    latencyMs = 10,
                    packetLossPercentage = 30,
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with packet loss data") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.icmp.packet.loss.latest.percentage"
                    expectedMeter shouldHaveNameTag enabledMonitorWithPacketLoss.name
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new up events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithPacketLoss = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutPacketLoss = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-data",
                    enabled = true,
                )
                val disabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitorWithPacketLoss.id,
                    latencyMs = 10,
                    packetLossPercentage = 50,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 30,
                )

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(enabledMonitorWithPacketLoss, null, latencyInMs = 10, packetLossPercentage = 10)
                )
                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(
                        enabledMonitorWithoutPacketLoss,
                        null,
                        latencyInMs = 10,
                        packetLossPercentage = 40
                    )
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forOne { withPreviousData ->
                        withPreviousData shouldHaveNameTag enabledMonitorWithPacketLoss.name
                        withPreviousData shouldHaveValue 10.0
                    }
                    registeredMeters.forOne { withoutPreviousData ->
                        withoutPreviousData shouldHaveNameTag enabledMonitorWithoutPacketLoss.name
                        withoutPreviousData shouldHaveValue 40.0
                    }
                }
            }

            `when`("there is a down event after initialization") {
                appContext = testAppContext()

                val enabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 20,
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    IcmpMonitorDownEvent(enabledMonitor, "timeout", previousEvent = null, packetLossPercentage = 100)
                )

                then("it should update the meter with the down event's packet loss value") {
                    meterRegistry().meters.single() shouldHaveValue 100.0
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitor = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpMetricsLogRepository().insertLog(
                    enabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 10,
                )
                icmpMetricsLogRepository().insertLog(
                    anotherEnabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 20,
                )
                icmpMetricsLogRepository().insertLog(
                    disabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 30,
                )
                icmpMetricsLogRepository().insertLog(
                    yetAnotherEnabledMonitor.id,
                    latencyMs = 10,
                    packetLossPercentage = 40,
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                icmpMonitorActions().updateMonitor(enabledMonitor.id, monitorDisableUpdate)
                icmpMonitorActions().updateMonitor(anotherEnabledMonitor.id, monitorNameUpdate)
                icmpMonitorActions().updateMonitor(disabledMonitor.id, monitorEnableUpdate)
                icmpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitor.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitor.name }
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitor.name }
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 20.0
                    }
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitor.name
                        newlyEnabledMonitor shouldHaveValue 30.0
                    }
                }
            }
        }
    }
}
