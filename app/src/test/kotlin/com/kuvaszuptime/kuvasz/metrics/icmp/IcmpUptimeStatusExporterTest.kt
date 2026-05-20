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

class IcmpUptimeStatusExporterTest : IcmpExporterTest("enabled-metrics-icmp-uptime-status") {

    init {
        given("an enabled ICMP uptime status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        enabledMonitorWithStatus,
                        previousEvent = null,
                        latencyInMs = 10,
                        packetLossPercentage = 0,
                    )
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        disabledMonitorWithStatus,
                        previousEvent = null,
                        latencyInMs = 10,
                        packetLossPercentage = 0,
                    )
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.icmp.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        enabledMonitorWithStatus,
                        previousEvent = null,
                        latencyInMs = 10,
                        packetLossPercentage = 0,
                    )
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        disabledMonitorWithStatus,
                        previousEvent = null,
                        latencyInMs = 10,
                        packetLossPercentage = 0,
                    )
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    IcmpMonitorDownEvent(
                        enabledMonitorWithStatus,
                        previousEvent = firstMonitorPreviousEvent,
                        error = "timeout",
                        packetLossPercentage = 100,
                    )
                )
                eventDispatcher().dispatch(
                    IcmpMonitorUpEvent(
                        enabledMonitorWithoutStatus,
                        previousEvent = null,
                        latencyInMs = 5,
                        packetLossPercentage = 0,
                    )
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

                val enabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createIcmpMonitor(
                    icmpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(enabledMonitorWithStatus, null, latencyInMs = 10, packetLossPercentage = 0)
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        anotherEnabledMonitorWithStatus,
                        null,
                        latencyInMs = 10,
                        packetLossPercentage = 0
                    )
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(
                        yetAnotherEnabledMonitorWithStatus,
                        null,
                        latencyInMs = 10,
                        packetLossPercentage = 0
                    )
                )
                icmpUptimeEventRepository().insertFromMonitorEvent(
                    IcmpMonitorUpEvent(disabledMonitorWithStatus, null, latencyInMs = 10, packetLossPercentage = 0)
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                icmpMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                icmpMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                icmpMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                icmpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

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
