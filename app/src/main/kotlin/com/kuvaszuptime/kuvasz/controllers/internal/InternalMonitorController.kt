package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.controllers.API_INTERNAL_PREFIX
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden

@Controller("$API_INTERNAL_PREFIX/monitors")
@Hidden
class InternalMonitorController(
    private val sharedMonitorRepository: SharedMonitorRepository,
) : InternalMonitorOperations {

    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitors(): List<MonitorID> = sharedMonitorRepository.fetchMonitorIds()
}
