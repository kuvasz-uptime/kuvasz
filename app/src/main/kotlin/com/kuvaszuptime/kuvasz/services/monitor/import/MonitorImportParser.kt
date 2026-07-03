package com.kuvaszuptime.kuvasz.services.monitor.import

import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportDto

interface MonitorImportParser {
    fun parse(content: ByteArray): MonitorImportDto
}
