package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.scheduling.TaskScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.time.Duration
import java.util.concurrent.ScheduledFuture

class MaintenanceWindowSchedulerTest : StringSpec({

    fun manualWindow(id: Long = 1, enabled: Boolean = true) = MaintenanceWindowRecord()
        .setId(id)
        .setName("manual-$id")
        .setEnabled(enabled)

    fun singleWindow(id: Long, start: java.time.OffsetDateTime, duration: String = "PT30M", enabled: Boolean = true) =
        MaintenanceWindowRecord()
            .setId(id)
            .setName("single-$id")
            .setEnabled(enabled)
            .setStart(start)
            .setDuration(duration)

    fun cronWindow(id: Long, cron: String = "0 2 * * *", duration: String = "PT1H") =
        MaintenanceWindowRecord()
            .setId(id)
            .setName("cron-$id")
            .setEnabled(true)
            .setCron(cron)
            .setDuration(duration)

    fun setup(): SchedulerFixture {
        val runnables = mutableListOf<Runnable>()
        val durations = mutableListOf<Duration>()
        val future = mockk<ScheduledFuture<*>>(relaxed = true)
        val taskScheduler = mockk<TaskScheduler>()
        val dispatcher = mockk<EventDispatcher>(relaxed = true)
        val repository = mockk<MaintenanceWindowRepository>(relaxed = true)
        val scheduler = MaintenanceWindowScheduler(taskScheduler, dispatcher, repository, MaintenanceWindowCalculator())

        every { taskScheduler.schedule(capture(durations), capture(runnables)) } returns future

        return SchedulerFixture(scheduler, taskScheduler, dispatcher, repository, runnables, durations, future)
    }

    "a manual window is never scheduled" {
        val f = setup()
        f.scheduler.scheduleWindow(manualWindow())
        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a disabled time-based window is never scheduled" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1), enabled = false))
        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
    }

    "a future single window schedules a start task that emits START then schedules the END" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))

        verify(exactly = 1) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }

        // Fire the start task
        f.runnables.single().run()

        val started = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(started)) }
        started.captured.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        // The start task scheduled the matching end task
        f.runnables shouldHaveSize 2
    }

    "a single window that is already mid-interval schedules only the END (no START is re-emitted)" {
        val f = setup()
        // Started 10 minutes ago, lasts 1 hour -> currently active
        f.scheduler.scheduleWindow(
            singleWindow(
                id = 1,
                start = getCurrentTimestamp().minusMinutes(10),
                duration = "PT1H",
            )
        )

        verify(exactly = 1) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }

        f.runnables.single().run()

        val ended = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(ended)) }
        ended.captured.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
        // A single window has no further occurrence
        f.runnables shouldHaveSize 1
    }

    "a cron window perpetuates itself: START schedules END, END schedules the next START" {
        val f = setup()
        f.scheduler.scheduleWindow(cronWindow(id = 1))

        // Initial start task
        f.runnables shouldHaveSize 1
        f.runnables[0].run() // START fires -> schedules END
        f.runnables shouldHaveSize 2
        f.runnables[1].run() // END fires -> schedules the next START
        f.runnables shouldHaveSize 3

        val dispatched = mutableListOf<MaintenanceWindowEvent>()
        verify(exactly = 2) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched[0].shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        dispatched[1].shouldBeInstanceOf<MaintenanceWindowEndEvent>()
    }

    "initialize schedules every enabled window, skipping manual ones" {
        val f = setup()
        every { f.repository.fetchByEnabled(true) } returns listOf(
            manualWindow(id = 1),
            singleWindow(id = 2, start = getCurrentTimestamp().plusHours(1)),
        )

        f.scheduler.initialize()

        verify(exactly = 1) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows() shouldContainKey 2L
    }

    "cancelWindow cancels the scheduled tasks of a window" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))

        f.scheduler.cancelWindow(1)

        verify { f.future.cancel(false) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "re-scheduling a window cancels the previously scheduled tasks" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(2)))

        verify { f.future.cancel(false) }
    }

    "onWindowCreated emits a START when an enabled manual window is created" {
        val f = setup()
        f.scheduler.onWindowCreated(manualWindow(id = 1, enabled = true))

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        // A manual window is never scheduled
        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
    }

    "onWindowCreated emits a START and schedules the END for a time-based window created mid-interval" {
        val f = setup()
        f.scheduler.onWindowCreated(
            singleWindow(id = 1, start = getCurrentTimestamp().minusMinutes(10), duration = "PT1H")
        )

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        // Only the remaining END task is scheduled (the START was emitted synchronously, not scheduled)
        f.scheduler.getScheduledWindows() shouldContainKey 1L
        f.runnables shouldHaveSize 1
    }

    "onWindowCreated emits nothing for a disabled manual window" {
        val f = setup()
        f.scheduler.onWindowCreated(manualWindow(id = 1, enabled = false))

        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "onWindowCreated schedules a future window without emitting a START" {
        val f = setup()
        f.scheduler.onWindowCreated(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))

        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
        // The upcoming START task is scheduled; the event fires only when it runs
        verify(exactly = 1) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows() shouldContainKey 1L
    }

    "onWindowUpdated emits a START when a manual window becomes enabled" {
        val f = setup()
        f.scheduler.onWindowUpdated(
            previous = manualWindow(id = 1, enabled = false),
            updated = manualWindow(id = 1, enabled = true),
        )

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        // A manual window is never scheduled
        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
    }

    "onWindowUpdated emits an END when a manual window becomes disabled" {
        val f = setup()
        f.scheduler.onWindowUpdated(
            previous = manualWindow(id = 1, enabled = true),
            updated = manualWindow(1, enabled = false),
        )

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
    }

    "onWindowUpdated emits nothing when a manual window's enabled flag did not change" {
        val f = setup()
        f.scheduler.onWindowUpdated(
            previous = manualWindow(id = 1, enabled = true),
            updated = manualWindow(id = 1, enabled = true),
        )
        f.scheduler.onWindowUpdated(manualWindow(2, enabled = false), manualWindow(2, enabled = false))

        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
    }

    "onWindowUpdated reschedules a time-based window without emitting a manual event" {
        val f = setup()
        f.scheduler.onWindowUpdated(
            previous = singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1), enabled = false),
            updated = singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1), enabled = true),
        )

        // The window got (re)scheduled, but no manual toggle event was emitted synchronously
        verify(exactly = 1) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows() shouldContainKey 1L
        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
    }

    "onWindowUpdated emits an END when an active time-based window is disabled" {
        val f = setup()
        val active = singleWindow(id = 1, start = getCurrentTimestamp().minusMinutes(10), duration = "PT1H")

        f.scheduler.onWindowUpdated(
            previous = active,
            updated = singleWindow(id = 1, start = active.start, duration = "PT1H", enabled = false),
        )

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
        // A disabled window has no scheduled tasks left to emit the END itself
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "onWindowUpdated emits an END when an active window is converted to a disabled manual window" {
        val f = setup()
        val active = singleWindow(id = 1, start = getCurrentTimestamp().minusMinutes(10), duration = "PT1H")

        f.scheduler.onWindowUpdated(previous = active, updated = manualWindow(id = 1, enabled = false))

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
    }

    "onWindowUpdated emits nothing when an active window stays active (still enabled and running)" {
        val f = setup()
        val active = singleWindow(id = 1, start = getCurrentTimestamp().minusMinutes(10), duration = "PT1H")

        f.scheduler.onWindowUpdated(previous = active, updated = active)

        // No synchronous event: the remaining END task keeps driving the maintenance state
        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
        f.scheduler.getScheduledWindows() shouldContainKey 1L
    }

    "onWindowUpdated emits a START when a time-based window is edited into an active state" {
        val f = setup()
        val start = getCurrentTimestamp().minusMinutes(10)

        f.scheduler.onWindowUpdated(
            previous = singleWindow(id = 1, start = start, duration = "PT1H", enabled = false),
            updated = singleWindow(id = 1, start = start, duration = "PT1H", enabled = true),
        )

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
        // The window is now active, so only its remaining END task is scheduled
        f.scheduler.getScheduledWindows() shouldContainKey 1L
    }

    "onWindowDeleted emits an END and cancels the tasks of an active window" {
        val f = setup()
        val active = singleWindow(id = 1, start = getCurrentTimestamp().minusMinutes(10), duration = "PT1H")
        f.scheduler.scheduleWindow(active)

        f.scheduler.onWindowDeleted(active)

        val dispatched = slot<MaintenanceWindowEvent>()
        verify(exactly = 1) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched.captured.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
        verify { f.future.cancel(false) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "onWindowDeleted emits nothing when the deleted window was not active" {
        val f = setup()
        val upcoming = singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1), duration = "PT1H")
        f.scheduler.scheduleWindow(upcoming)

        f.scheduler.onWindowDeleted(upcoming)

        verify(inverse = true) { f.dispatcher.dispatch(any<MaintenanceWindowEvent>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a single window that is entirely in the past is not scheduled" {
        val f = setup()
        // Started 2 hours ago and only lasted 30 minutes -> neither active nor upcoming
        f.scheduler.scheduleWindow(
            singleWindow(
                id = 1,
                start = getCurrentTimestamp().minusHours(2),
                duration = "PT30M",
            )
        )

        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a window with an invalid cron expression is not scheduled" {
        val f = setup()
        f.scheduler.scheduleWindow(cronWindow(id = 1, cron = "not-a-valid-cron"))

        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a window with an invalid duration is not scheduled" {
        val f = setup()
        f.scheduler.scheduleWindow(
            singleWindow(
                id = 1,
                start = getCurrentTimestamp().plusHours(1),
                duration = "nonsense",
            )
        )

        verify(inverse = true) { f.taskScheduler.schedule(any<Duration>(), any<Runnable>()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a cron window that is already mid-interval schedules only the END, then continues to perpetuate" {
        val f = setup()
        // Fires every minute, lasts an hour -> the previous fire is still active right now
        f.scheduler.scheduleWindow(cronWindow(id = 1, cron = "* * * * *", duration = "PT1H"))

        // Only the remaining END task is scheduled, no START is re-emitted
        f.runnables shouldHaveSize 1
        f.runnables[0].run() // END fires -> schedules the next START
        f.runnables shouldHaveSize 2
        f.runnables[1].run() // next START fires -> emits START and schedules its END
        f.runnables shouldHaveSize 3

        val dispatched = mutableListOf<MaintenanceWindowEvent>()
        verify(exactly = 2) { f.dispatcher.dispatch(capture(dispatched)) }
        dispatched[0].shouldBeInstanceOf<MaintenanceWindowEndEvent>()
        dispatched[1].shouldBeInstanceOf<MaintenanceWindowStartEvent>()
    }

    "scheduled tasks of the same window accumulate under its id" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))
        f.scheduler.getScheduledWindows().getValue(1) shouldHaveSize 1

        f.runnables.single().run() // START fires -> schedules its END under the same id

        f.scheduler.getScheduledWindows().getValue(1) shouldHaveSize 2
    }

    "completed futures are pruned so a cron window's task list stays bounded" {
        val runnables = mutableListOf<Runnable>()
        val futures = mutableListOf<ScheduledFuture<*>>()
        val taskScheduler = mockk<TaskScheduler>()
        val dispatcher = mockk<EventDispatcher>(relaxed = true)
        val repository = mockk<MaintenanceWindowRepository>(relaxed = true)
        val scheduler = MaintenanceWindowScheduler(taskScheduler, dispatcher, repository, MaintenanceWindowCalculator())

        // Each scheduled task gets its own future that only reports "done" once we mark it so
        every { taskScheduler.schedule(any<Duration>(), capture(runnables)) } answers {
            mockk<ScheduledFuture<*>>(relaxed = true).also { future -> futures.add(future) }
        }

        scheduler.scheduleWindow(cronWindow(id = 1, cron = "* * * * *", duration = "PT30M"))

        // Drive the self-perpetuating cron chain many times, marking each fired task done afterwards
        repeat(50) { i ->
            runnables[i].run()
            every { futures[i].isDone } returns true
        }

        scheduler.getScheduledWindows().getValue(1) shouldHaveSize 2
    }

    "multiple windows are scheduled and cancelled independently" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))
        f.scheduler.scheduleWindow(singleWindow(id = 2, start = getCurrentTimestamp().plusHours(2)))

        f.scheduler.getScheduledWindows().keys shouldContainExactlyInAnyOrder listOf(1L, 2L)

        f.scheduler.cancelWindow(1)

        f.scheduler.getScheduledWindows() shouldContainKey 2L
        f.scheduler.getScheduledWindows() shouldNotContainKey 1L
    }

    "cancelling an unscheduled window is a no-op" {
        val f = setup()
        f.scheduler.cancelWindow(404)

        verify(inverse = true) { f.future.cancel(any()) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "getScheduledWindows returns a snapshot that is decoupled from the internal state" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))

        val snapshot = f.scheduler.getScheduledWindows()
        f.scheduler.cancelWindow(1)

        // The previously obtained snapshot is unaffected by the subsequent cancellation
        snapshot shouldContainKey 1L
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "close cancels every scheduled task of every window and clears the registry" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))
        f.scheduler.scheduleWindow(singleWindow(id = 2, start = getCurrentTimestamp().plusHours(2)))

        f.scheduler.close()

        verify(exactly = 2) { f.future.cancel(false) }
        f.scheduler.getScheduledWindows().shouldBeEmpty()
    }

    "a future start is scheduled with a positive delay" {
        val f = setup()
        f.scheduler.scheduleWindow(singleWindow(id = 1, start = getCurrentTimestamp().plusHours(1)))

        f.durations.single() shouldBeGreaterThan Duration.ZERO
    }

    "a target that is already in the past is clamped to a zero delay" {
        mockkStatic("com.kuvaszuptime.kuvasz.util.DateExtKt")
        try {
            var now = getCurrentTimestamp()
            every { getCurrentTimestamp() } answers { now }

            val f = setup()
            val start = now.plusHours(1)
            f.scheduler.scheduleWindow(singleWindow(1, start, duration = "PT1H"))
            // The initial START task is scheduled with a positive delay (~1 hour ahead)
            f.durations.single() shouldBeGreaterThan Duration.ZERO

            // Time jumps beyond the window's end, then the START task finally fires and schedules its END
            now = start.plusHours(2)
            f.runnables.single().run()

            // The END target is now in the past, so its delay must be clamped to zero (never negative)
            f.durations.last() shouldBe Duration.ZERO
        } finally {
            unmockkStatic("com.kuvaszuptime.kuvasz.util.DateExtKt")
        }
    }
})

private data class SchedulerFixture(
    val scheduler: MaintenanceWindowScheduler,
    val taskScheduler: TaskScheduler,
    val dispatcher: EventDispatcher,
    val repository: MaintenanceWindowRepository,
    val runnables: MutableList<Runnable>,
    val durations: MutableList<Duration>,
    val future: ScheduledFuture<*>,
)
