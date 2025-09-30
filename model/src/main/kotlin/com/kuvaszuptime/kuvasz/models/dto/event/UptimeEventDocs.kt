package com.kuvaszuptime.kuvasz.models.dto.event

object UptimeEventDocs {
    const val ID = "Unique identifier for the uptime event"
    const val ERROR = "The error that occurred during the uptime check, if any"
    const val UPTIME_STATUS = "The status of the uptime event"
    const val STARTED_AT = "The timestamp when the uptime event started"
    const val ENDED_AT = "The timestamp when the uptime event ended, if applicable"
    const val UPDATED_AT = "The timestamp when the uptime event was updated"
}
