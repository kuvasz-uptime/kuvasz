package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import jakarta.inject.Singleton

@Singleton
class StatusPageCrudService(
    private val statusPageRepository: StatusPageRepository,
) {
    fun getStatusPagesExport(): List<StatusPageRecord> = statusPageRepository.fetchAll()
}
