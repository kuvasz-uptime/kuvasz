package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import org.jooq.DSLContext

interface ImportStrategy {
    fun execute(validatedImport: ValidatedMonitorImport, txCtx: DSLContext): List<MonitorTypeImportResult>
}
