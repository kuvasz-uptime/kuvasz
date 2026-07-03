package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto

interface MonitorImportParser {
    fun parse(content: ByteArray): MonitorImportDto
}
