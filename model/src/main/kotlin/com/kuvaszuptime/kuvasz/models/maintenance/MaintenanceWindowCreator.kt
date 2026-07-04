package com.kuvaszuptime.kuvasz.models.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.validation.ValidCron
import com.kuvaszuptime.kuvasz.validation.ValidDuration
import com.kuvaszuptime.kuvasz.validation.isValidCron
import com.kuvaszuptime.kuvasz.validation.isValidDuration
import jakarta.validation.ValidationException
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

/**
 * The schedule-defining fields of a maintenance window. Used to validate that the combination of `cron`, `start` and
 * `duration` describes exactly one valid window type (see [MaintenanceWindowType]).
 */
interface MaintenanceSchedule {
    val cron: String?
    val start: OffsetDateTime?
    val duration: String?
}

enum class MaintenanceWindowType {
    // Both `cron` and `start` are unset; the window is toggled purely via `enabled`.
    MANUAL,

    // Recurring schedule defined by `cron` + `duration`.
    CRON,

    // One-shot schedule defined by `start` + `duration`.
    SINGLE,
}

/**
 * Resolves the [MaintenanceWindowType] of a schedule, or `null` if the combination is invalid (e.g. both `cron` and
 * `start` set, or a time-based schedule missing its `duration`).
 */
private fun MaintenanceSchedule.resolveType(): MaintenanceWindowType? = when {
    cron != null && start != null -> null
    cron != null -> duration?.let { MaintenanceWindowType.CRON }
    start != null -> duration?.let { MaintenanceWindowType.SINGLE }
    else -> MaintenanceWindowType.MANUAL
}

/**
 * Validates the schedule before it is persisted, guarding every write path (REST create/update and YAML import) so
 * that no malformed schedule can reach the DB. Throws a [ValidationException] if:
 * - the combination of `cron`/`start`/`duration` does not describe a valid window type (mirrors the DB CHECK
 *   constraints on the `maintenance_window` table), or
 * - `cron` is set but is not a valid cron expression, or
 * - `duration` is set but is not a valid, strictly positive ISO-8601 duration.
 */
fun MaintenanceSchedule.validateScheduleConsistency() {
    val errorMessage = when {
        resolveType() == null -> MaintenanceWindowValidationMessages.SCHEDULE_INVALID
        cron?.let { !isValidCron(it) } == true -> MaintenanceWindowValidationMessages.CRON_INVALID
        duration?.let { !isValidDuration(it) } == true -> MaintenanceWindowValidationMessages.DURATION_INVALID
        else -> null
    }
    if (errorMessage != null) {
        throw ValidationException(errorMessage)
    }
}

interface MaintenanceWindowCreator : MaintenanceSchedule {
    @get:NotBlank(message = MaintenanceWindowValidationMessages.NAME_NOT_BLANK)
    val name: String
    val description: String?
    val enabled: Boolean
    val global: Boolean
    val showOnStatusPages: Boolean

    @get:ValidCron
    override val cron: String?

    @get:ValidDuration
    override val duration: String?

    val monitors: List<String>?
    val integrations: List<String>?
}

fun MaintenanceWindowCreator.toMaintenanceWindowRecord(
    validatedMonitors: Set<MonitorID>,
    validatedIntegrations: Set<IntegrationID>,
): MaintenanceWindowRecord =
    MaintenanceWindowRecord()
        .setName(name)
        .setDescription(description)
        .setEnabled(enabled)
        .setGlobal(global)
        .setShowOnStatusPages(showOnStatusPages)
        .setCron(cron)
        .setStart(start)
        .setDuration(duration)
        .setMonitors(validatedMonitors.toTypedArray())
        .setIntegrations(validatedIntegrations.toTypedArray())
