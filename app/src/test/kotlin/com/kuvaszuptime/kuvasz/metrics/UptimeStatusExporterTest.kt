package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus

class UptimeStatusExporterTest : ExporterTest("enabled-metrics-uptime-status") {

    init {
        given("an enabled status exporter") {

            `when`("the exporter is initialized") {
                appContext = ApplicationContext.run()

                val enabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    url = "https://test.enabled.no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        enabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 20,
                        previousEvent = null,
                    )
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        disabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 10,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.monitor.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveUrlTag enabledMonitorWithStatus.url
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = ApplicationContext.run()

                val enabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    url = "https://test.enabled.no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        enabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 20,
                        previousEvent = null,
                    )
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        disabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 10,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    MonitorDownEvent(
                        enabledMonitorWithStatus,
                        status = HttpStatus.SERVICE_UNAVAILABLE,
                        previousEvent = firstMonitorPreviousEvent,
                        error = Exception("irrelevant")
                    )
                )
                eventDispatcher().dispatch(
                    MonitorUpEvent(
                        enabledMonitorWithoutStatus,
                        status = HttpStatus.OK,
                        latency = 25,
                        previousEvent = null
                    )
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the enabled monitor with status should be updated
                    registeredMeters.forOne { withPreviousStatus ->
                        withPreviousStatus shouldHaveNameTag enabledMonitorWithStatus.name
                        withPreviousStatus shouldHaveValue 0.0 // The status is down, so the value should be 0
                    }
                    // The meter for the enabled monitor without status should be created
                    registeredMeters.forOne { withoutPreviousStatus ->
                        withoutPreviousStatus shouldHaveNameTag enabledMonitorWithoutStatus.name
                        withoutPreviousStatus shouldHaveValue 1.0 // The status is up, so the value should be 1
                    }
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = ApplicationContext.run()

                val enabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-other",
                    url = "https://test.enabled.other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    url = "https://yet.another.enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                )

                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        enabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 20,
                        previousEvent = null,
                    )
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        anotherEnabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 30,
                        previousEvent = null,
                    )
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        yetAnotherEnabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 25,
                        previousEvent = null,
                    )
                )
                uptimeEventRepository().insertFromMonitorEvent(
                    MonitorUpEvent(
                        disabledMonitorWithStatus,
                        status = HttpStatus.OK,
                        latency = 10,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                monitorCrudService().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                monitorCrudService().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                monitorCrudService().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                monitorCrudService().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

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
