package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.models.MonitorType

interface MonitorCheckScheduler {

    val monitorType: MonitorType

    /**
     * Schedules the checks of every enabled monitor of the given type.
     */
    fun initialize()

    /**
     * Removes all the checks from the scheduler.
     */
    fun removeAllChecks()
}
