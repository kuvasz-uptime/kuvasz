package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.dto.import.MonitorTypeImportResult
import org.jooq.DSLContext

interface ImportStrategy {
    fun execute(validatedImport: ValidatedMonitorImport, txCtx: DSLContext): List<MonitorTypeImportResult>
}
