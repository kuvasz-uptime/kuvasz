package com.kuvaszuptime.kuvasz.metrics.tcp

import com.kuvaszuptime.kuvasz.metrics.TcpExporterTest
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TcpUptimeStatusExporterTest : TcpExporterTest("enabled-metrics-tcp-uptime-status") {

    init {
        given("an enabled TCP uptime status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.tcp.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    TcpMonitorDownEvent(
                        enabledMonitorWithStatus,
                        previousEvent = firstMonitorPreviousEvent,
                        error = "timeout",
                    )
                )
                eventDispatcher().dispatch(
                    TcpMonitorUpEvent(enabledMonitorWithoutStatus, previousEvent = null, latencyInMs = 5)
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

                val enabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(enabledMonitorWithStatus, null, latencyInMs = 10)
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(anotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(yetAnotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                tcpUptimeEventRepository().insertFromMonitorEvent(
                    TcpMonitorUpEvent(disabledMonitorWithStatus, null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                tcpMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                tcpMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                tcpMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                tcpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

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
