package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.micronaut.http.annotation.Controller
import io.swagger.v3.oas.annotations.Hidden

@Controller("$API_INTERNAL_PREFIX/monitors/categories")
@Hidden
class InternalMonitorController(
    private val monitorRepositories: List<MonitorRepository<*, *>>,
) : InternalMonitorOperations {

    override fun getCategories(): List<String> = monitorRepositories
        .flatMap { it.fetchDistinctCategories() }
        .distinct()
        .sortedBy { it.lowercase() }
}
