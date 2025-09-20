package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.InvalidMonitorIdException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import jakarta.inject.Singleton

@Singleton
class MonitorIdValidator(private val httpMonitorRepository: HttpMonitorRepository) {

    private fun MonitorID.checkIfConfigured(): MonitorID? =
        when (type) {
            MonitorType.HTTP_SSL -> httpMonitorRepository.findByName(name)
        }?.monitorId()

    /**
     * Validates an array of monitor IDs against the existing monitors.
     */
    fun validateMonitorIds(ids: Array<MonitorID>) = ids.mapNotNull { id -> id.checkIfConfigured() }

    /**
     * Validates a list of monitor IDs against the existing monitors and returns a set of valid,
     * existing monitor IDs.
     *
     * @return a set of valid monitor IDs.
     * @throws InvalidMonitorIdException if any of the provided IDs are not valid.
     */
    fun validateMonitorIds(rawIds: List<String>): Set<MonitorID> = rawIds.mapNotNull { id ->
        (MonitorID.fromString(id) ?: throw InvalidMonitorIdException(id)).checkIfConfigured()
    }.toSet()
}
