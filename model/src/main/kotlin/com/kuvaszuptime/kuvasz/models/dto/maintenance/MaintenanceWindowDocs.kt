package com.kuvaszuptime.kuvasz.models.dto.maintenance

object MaintenanceWindowDocs {
    const val ID = "Unique identifier of the maintenance window"
    const val NAME = "Unique name of the maintenance window"
    const val DESCRIPTION = "Optional human-readable description of the maintenance window"
    const val ENABLED = "Master on/off switch. For cron and single windows this is necessary but not sufficient: " +
        "the schedule still decides whether the window is currently active. A manual window (no 'cron' and no " +
        "'start') is active whenever this is true."
    const val GLOBAL = "Whether the window applies to every monitor, regardless of the 'monitors' list"
    const val SHOW_ON_STATUS_PAGES = "Whether the window can be displayed on status pages"
    const val CRON = "Cron expression for a recurring window (standard cron syntax, but supports extensions like " +
        "#, L, W). Mutually exclusive with 'start' and requires 'duration'. Evaluated in the server's time zone."
    const val START = "Start timestamp for a single, one-shot window (may be in the past). Mutually exclusive with " +
        "'cron' and requires 'duration'."
    const val DURATION = "ISO-8601 duration of the window (e.g. 'PT1H30M'). Required for cron and single windows."
    const val MONITORS = "Set of monitor IDs the window applies to (ignored when 'global' is true)"
    const val INTEGRATIONS = "Set of integration IDs that receive the window's start and end notifications"
    const val ACTIVE = "Whether the window is currently active"
    const val NEXT_START = "Timestamp of the next time the window will start, or null if there is no future occurrence"
    const val ENDS_AT = "Timestamp when the currently active window will end, or null if it is not active or is " +
        "an indefinitely running manual window"
    const val CREATED_AT = "Timestamp of when the window was created"
    const val UPDATED_AT = "Timestamp of when the window was last updated"
    const val MAINTENANCE_WINDOWS_405_REASON =
        "Maintenance windows are in read-only mode, because they are loaded from a YAML config file"
}
