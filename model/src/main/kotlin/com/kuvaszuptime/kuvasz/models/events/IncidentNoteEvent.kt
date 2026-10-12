package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto

sealed class IncidentNoteEvent {
    abstract val note: IncidentNoteDto
}

data class IncidentNoteCreatedEvent(override val note: IncidentNoteDto) : IncidentNoteEvent()

data class IncidentNoteUpdatedEvent(override val note: IncidentNoteDto) : IncidentNoteEvent()
