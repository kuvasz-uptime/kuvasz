package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.time.OffsetDateTime

sealed class MaintenanceWindowEvent : NotifiableEvent {
    abstract val window: MaintenanceWindowRecord
    val dispatchedAt: OffsetDateTime = getCurrentTimestamp()
}

data class MaintenanceWindowStartEvent(override val window: MaintenanceWindowRecord) : MaintenanceWindowEvent()

data class MaintenanceWindowEndEvent(override val window: MaintenanceWindowRecord) : MaintenanceWindowEvent()
