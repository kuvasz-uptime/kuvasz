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

class TcpLatencyExporterTest : TcpExporterTest("enabled-metrics-tcp-latency") {

    init {
        given("an enabled TCP latency exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without latency records
                createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100)
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20)
                tcpMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with latency") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.tcp.latency.latest.milliseconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithLatency.name
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 90)
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20)
                tcpMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    TcpMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = 30)
                )
                eventDispatcher().dispatch(
                    TcpMonitorUpEvent(enabledMonitorWithoutLatency, null, latencyInMs = 40)
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

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    TcpMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("there is a down event carrying a latency (latency threshold breached)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    TcpMonitorDownEvent(
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

            `when`("there is a down event without a latency (connection failed)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    TcpMonitorDownEvent(enabledMonitorWithLatency, error = "refused", previousEvent = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createTcpMonitor(
                    tcpMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                tcpMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100)
                tcpMetricsLogRepository().insertLog(anotherEnabledMonitorWithLatency.id, latencyMs = 20)
                tcpMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50)
                tcpMetricsLogRepository().insertLog(yetAnotherEnabledMonitorWithLatency.id, latencyMs = 10)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                tcpMonitorActions().updateMonitor(enabledMonitorWithLatency.id, monitorDisableUpdate)
                tcpMonitorActions().updateMonitor(anotherEnabledMonitorWithLatency.id, monitorNameUpdate)
                tcpMonitorActions().updateMonitor(disabledMonitorWithLatency.id, monitorEnableUpdate)
                tcpMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithLatency.id)

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
