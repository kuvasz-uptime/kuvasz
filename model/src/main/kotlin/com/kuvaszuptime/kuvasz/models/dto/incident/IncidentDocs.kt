package com.kuvaszuptime.kuvasz.models.dto.incident

object IncidentDocs {
    const val EVENT_ID = "ID of the event behind the incident: the ID of the DOWN uptime event, or the ID of the " +
        "INVALID SSL event in case of SSL incidents. Unique only together with the incident type"
    const val INCIDENT = "The incident itself"
    const val NOTES = "The notes of the incident, ordered by their timestamp (or their creation date, if they " +
        "don't have one)"
    const val INTEGRATIONS = "The integrations that were notified when the incident started"
    const val RESOLUTION_INTEGRATIONS = "The integrations that were notified when the incident was resolved, null " +
        "if the incident is still ongoing"
}

object IncidentNoteDocs {
    const val INCIDENT_NOTES_405_REASON = "Incident notes are disabled on this instance (ENABLE_INCIDENT_NOTES=false)"
    const val ID = "Unique identifier of the incident note"
    const val INCIDENT_TYPE = "Type of the incident the note belongs to"
    const val EVENT_ID = "ID of the event behind the incident the note belongs to, see the incident's eventId"
    const val STATUS = "The state of the investigation the note describes"
    const val CONTENT = "The content of the note, as plain text"
    const val TIMESTAMP = "Optional timestamp of the note, which overrides its creation date where it's displayed " +
        "(e.g. when it's added later), and orders the notes of the incident"
    const val PUBLIC = "Whether the note is shown on the status pages that contain the monitor of the incident. " +
        "SSL incidents are never shown on status pages, so it has no effect on them"
    const val CREATED_AT = "When the note was created"
    const val UPDATED_AT = "When the note was last updated"
}
