package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.gracefulCancel
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture

/**
 * Schedules the start/end notifications of time-based maintenance windows (cron + single). Manual windows have no
 * schedule; their start/end events are emitted synchronously by [onWindowUpdated] when their `enabled` flag is toggled.
 *
 * Scheduling rules (see the calculator for the actual time math):
 * - **single**: a start task at `start` (if it is still in the future) and an end task at `start + duration`.
 * - **cron**: a start task at the next fire time; once it fires it emits the start event and schedules both the
 *   matching end task and the following start task, perpetuating itself.
 * - **mid-interval at scheduling time** (e.g. after a restart, or when a window is created/updated while it is already
 *   running): no start event is re-emitted, only the remaining end task (and, for cron, the subsequent occurrences)
 *   are scheduled.
 *
 * Creates, edits and deletes can move a window between "active" and "inactive" out of band of its schedule (e.g.
 * creating an already-active window, or disabling a currently running one which discards its pending end task).
 * [onWindowCreated], [onWindowUpdated] and [onWindowDeleted] therefore emit the matching start/end event synchronously
 * whenever the window's active state flips, so consumers never get stranded (e.g. a PagerDuty incident opened on start
 * would otherwise never be resolved).
 */
@Singleton
class MaintenanceWindowScheduler(
    @param:Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val eventDispatcher: EventDispatcher,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val calculator: MaintenanceWindowCalculator,
) : AutoCloseable {

    private val scheduledTasks: ConcurrentHashMap<Long, CopyOnWriteArrayList<ScheduledFuture<*>>> = ConcurrentHashMap()

    fun initialize() {
        maintenanceWindowRepository.fetchByEnabled(enabled = true).forEach { scheduleWindow(it) }
        logger.info("Maintenance window scheduler has been initialized with ${scheduledTasks.size} scheduled window(s)")
    }

    fun getScheduledWindows(): Map<Long, List<ScheduledFuture<*>>> = scheduledTasks.toMap()

    /**
     * (Re)schedules a single window: cancels any previously scheduled tasks for it, then schedules the upcoming
     * start/end tasks based on its current schedule. Disabled and manual windows end up with no scheduled tasks.
     */
    fun scheduleWindow(window: MaintenanceWindowRecord) {
        cancelWindow(window.id)
        if (window.enabled != true || window.isManual()) return

        val now = getCurrentTimestamp()
        val currentInterval = calculator.currentInterval(window, now)
        if (currentInterval != null) {
            // The window is already running: do not re-emit the start, just schedule the remaining end (and, for cron,
            // the subsequent occurrences once the end fires).
            scheduleEnd(window, currentInterval.end)
        } else {
            calculator.nextInterval(window, now)?.let { scheduleStart(window, it.start, it.end) }
        }
    }

    /**
     * Schedules a freshly created window and, if it is already active (e.g. a manual window created enabled, or a
     * time-based one created mid-interval), emits its start event synchronously. Without this, creating an active
     * window would silently put monitors into maintenance with no start notification — unlike creating it disabled and
     * then toggling it on.
     */
    fun onWindowCreated(window: MaintenanceWindowRecord) {
        scheduleWindow(window)
        if (calculator.isActive(window)) {
            eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))
        }
    }

    fun onWindowUpdated(previous: MaintenanceWindowRecord, updated: MaintenanceWindowRecord) {
        scheduleWindow(updated)
        emitActiveStateTransition(previous, updated)
    }

    /**
     * Cancels a deleted window's scheduled tasks and, if it was active, emits its end event synchronously so the
     * maintenance state is closed for consumers (its pending end task is gone with the window).
     */
    fun onWindowDeleted(window: MaintenanceWindowRecord) {
        cancelWindow(window.id)
        if (calculator.isActive(window)) {
            eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))
        }
    }

    fun cancelWindow(windowId: Long) {
        scheduledTasks.remove(windowId)?.forEach { it.gracefulCancel() }
    }

    /**
     * Emits the start/end event for an edit that flips the window's active state out of band of its schedule
     * (e.g. disabling a running window, or converting one to a manual window). Edits that leave the active state
     * unchanged emit nothing: the schedule keeps driving the matching event for time-based windows.
     */
    private fun emitActiveStateTransition(previous: MaintenanceWindowRecord, updated: MaintenanceWindowRecord) {
        val now = getCurrentTimestamp()
        val wasActive = calculator.isActive(previous, now)
        val isActive = calculator.isActive(updated, now)
        when {
            !wasActive && isActive -> eventDispatcher.dispatch(MaintenanceWindowStartEvent(updated))
            wasActive && !isActive -> eventDispatcher.dispatch(MaintenanceWindowEndEvent(updated))
        }
    }

    private fun scheduleStart(window: MaintenanceWindowRecord, start: OffsetDateTime, end: OffsetDateTime) {
        val task = taskScheduler.schedule(delayUntil(start)) {
            eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))
            scheduleEnd(window, end)
        }
        register(window.id, task)
    }

    private fun scheduleEnd(window: MaintenanceWindowRecord, end: OffsetDateTime) {
        val task = taskScheduler.schedule(delayUntil(end)) {
            eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))
            // For recurring windows, line up the next occurrence after this one has ended.
            calculator.nextInterval(window, end)?.let { scheduleStart(window, it.start, it.end) }
        }
        register(window.id, task)
    }

    private fun register(windowId: Long, task: ScheduledFuture<*>) {
        scheduledTasks.computeIfAbsent(windowId) { CopyOnWriteArrayList() }.apply {
            removeIf { it.isDone }
            add(task)
        }
    }

    private fun delayUntil(target: OffsetDateTime): Duration =
        Duration.between(getCurrentTimestamp(), target).let { if (it.isNegative) Duration.ZERO else it }

    @PreDestroy
    override fun close() {
        scheduledTasks.values.forEach { tasks -> tasks.forEach { it.gracefulCancel() } }
        scheduledTasks.clear()
    }

    companion object {
        private val logger = loggerFor<MaintenanceWindowScheduler>()
    }
}
