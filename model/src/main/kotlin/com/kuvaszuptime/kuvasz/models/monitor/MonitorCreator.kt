package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID

interface MonitorCreator<R : MonitorRecord> {

    /**
     * The raw, still unvalidated integration references of the config.
     */
    val integrations: List<String>?

    fun toMonitorRecord(validatedIntegrations: Set<IntegrationID>): R
}
