package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto

fun MaintenanceWindowRecord.toDetailsDto(calculator: MaintenanceWindowCalculator): MaintenanceWindowDetailsDto =
    MaintenanceWindowDetailsDto.fromRecord(
        record = this,
        active = calculator.isActive(this),
        nextStart = calculator.nextInterval(this)?.start,
        endsAt = calculator.currentInterval(this)?.end,
    )
