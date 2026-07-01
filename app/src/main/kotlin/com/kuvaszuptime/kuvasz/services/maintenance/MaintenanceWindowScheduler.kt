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

    fun onWindowUpdated(previous: MaintenanceWindowRecord, updated: MaintenanceWindowRecord) {
        scheduleWindow(updated)
        emitManualToggleEvents(previous, updated)
    }

    fun cancelWindow(windowId: Long) {
        scheduledTasks.remove(windowId)?.forEach { it.gracefulCancel() }
    }

    private fun emitManualToggleEvents(previous: MaintenanceWindowRecord, updated: MaintenanceWindowRecord) {
        if (!updated.isManual()) return

        val wasEnabled = previous.enabled == true
        val isEnabled = updated.enabled == true
        when {
            !wasEnabled && isEnabled -> eventDispatcher.dispatch(MaintenanceWindowStartEvent(updated))
            wasEnabled && !isEnabled -> eventDispatcher.dispatch(MaintenanceWindowEndEvent(updated))
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
        scheduledTasks.computeIfAbsent(windowId) { CopyOnWriteArrayList() }.add(task)
    }

    private fun delayUntil(target: OffsetDateTime): Duration =
        Duration.between(getCurrentTimestamp(), target).let { if (it.isNegative) Duration.ZERO else it }

    private fun MaintenanceWindowRecord.isManual(): Boolean = cron == null && start == null

    @PreDestroy
    override fun close() {
        scheduledTasks.values.forEach { tasks -> tasks.forEach { it.gracefulCancel() } }
        scheduledTasks.clear()
    }

    companion object {
        private val logger = loggerFor<MaintenanceWindowScheduler>()
    }
}
