package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp

fun MaintenanceWindowRecord.isManual(): Boolean = cron == null && start == null

fun MaintenanceWindowRecord.toDetailsDto(calculator: MaintenanceWindowCalculator): MaintenanceWindowDetailsDto {
    val now = getCurrentTimestamp()
    val currentInterval = calculator.currentInterval(this, now)
    val nextInterval = calculator.nextInterval(this, now)

    return MaintenanceWindowDetailsDto.fromRecord(
        record = this,
        active = calculator.isActive(this, now),
        nextStart = nextInterval?.start,
        endsAt = currentInterval?.end,
    )
}
