package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.time.OffsetDateTime

sealed class MaintenanceWindowEvent : NotifiableEvent {
    abstract val window: MaintenanceWindowRecord
    val dispatchedAt: OffsetDateTime = getCurrentTimestamp()

    abstract fun toStructuredMessage(): StructuredMaintenanceMessage
}

data class MaintenanceWindowStartEvent(override val window: MaintenanceWindowRecord) : MaintenanceWindowEvent() {

    override fun toStructuredMessage() = StructuredMaintenanceStartMessage(
        summary = Messages.maintenanceWindowStarted(window.name),
        description = window.description?.takeIf { it.isNotBlank() },
    )
}

data class MaintenanceWindowEndEvent(override val window: MaintenanceWindowRecord) : MaintenanceWindowEvent() {

    override fun toStructuredMessage() = StructuredMaintenanceEndMessage(
        summary = Messages.maintenanceWindowEnded(window.name),
    )
}
