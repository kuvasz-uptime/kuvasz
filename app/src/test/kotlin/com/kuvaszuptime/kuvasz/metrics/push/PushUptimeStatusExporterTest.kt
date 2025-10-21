package com.kuvaszuptime.kuvasz.metrics.push

import com.kuvaszuptime.kuvasz.metrics.PushExporterTest
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PushUptimeStatusExporterTest : PushExporterTest("enabled-metrics-push-uptime-status") {

    init {
        given("an enabled status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        enabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        disabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.push.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        enabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        disabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    PushMonitorDownEvent(
                        enabledMonitorWithStatus,
                        previousEvent = firstMonitorPreviousEvent,
                        error = "irrelevant"
                    )
                )
                eventDispatcher().dispatch(
                    PushMonitorUpEvent(
                        enabledMonitorWithoutStatus,
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

                appContext = testAppContext()

                val enabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createPushMonitor(
                    pushMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        enabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        anotherEnabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        yetAnotherEnabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )
                pushUptimeEventRepository().insertFromMonitorEvent(
                    PushMonitorUpEvent(
                        disabledMonitorWithStatus,
                        previousEvent = null,
                    )
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                pushMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                pushMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                pushMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                pushMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

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
