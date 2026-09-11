package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import io.micronaut.http.annotation.Controller
import io.swagger.v3.oas.annotations.Hidden

@Controller("$API_INTERNAL_PREFIX/categories")
@Hidden
class InternalCategoryController(
    private val monitorRepositories: List<MonitorRepository<*, *>>,
    private val statusPageRepository: StatusPageRepository,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
) : InternalCategoryOperations {

    override fun getCategories(): List<String> = (
        monitorRepositories.flatMap { it.fetchDistinctCategories() } +
            statusPageRepository.fetchDistinctCategories() +
            maintenanceWindowRepository.fetchDistinctCategories()
        )
        .distinct()
        .sortedBy { it.lowercase() }
}
