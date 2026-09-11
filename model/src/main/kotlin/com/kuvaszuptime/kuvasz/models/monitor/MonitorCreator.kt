package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import jakarta.validation.constraints.Size

interface MonitorCreator<R : MonitorRecord> : WithCategory {

    /**
     * The raw, still unvalidated integration references of the config.
     */
    val integrations: List<String>?

    @get:Size(max = Validation.MAX_CATEGORY_LENGTH, message = MonitorValidationMessages.CATEGORY_MAX_SIZE)
    override val category: String?

    fun toMonitorRecord(validatedIntegrations: Set<IntegrationID>): R
}
