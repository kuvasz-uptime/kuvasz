package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.InvalidMonitorIdException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import jakarta.inject.Singleton

@Singleton
class MonitorIdValidator(private val httpMonitorRepository: HttpMonitorRepository) {

    private fun MonitorID.checkIfConfigured(): MonitorID {
        val existingMonitor = when (type) {
            MonitorType.HTTP_SSL -> httpMonitorRepository.findByName(name)
        }
        if (existingMonitor == null) {
            throw NonExistingMonitorIdException(this.toString())
        }
        return this
    }

    /**
     * Validates an array of monitor IDs against the existing monitors.
     *
     * @throws NonExistingMonitorIdException if any of the provided IDs are not existing.
     */
    fun validateMonitorIds(ids: Array<MonitorID>) = ids.forEach { id -> id.checkIfConfigured() }

    /**
     * Validates a list of monitor IDs against the existing monitors.
     *
     * @return a set of valid monitor IDs.
     * @throws NonExistingMonitorIdException if any of the provided IDs are not existing.
     * @throws InvalidMonitorIdException if any of the provided IDs are not valid.
     */
    fun validateMonitorIds(rawIds: List<String>): Set<MonitorID> = rawIds.map { id ->
        MonitorID.fromString(id)?.checkIfConfigured() ?: throw InvalidMonitorIdException(id)
    }.toSet()
}

class NonExistingMonitorIdException(monitorId: String) : RuntimeException(
    "Non-existing monitor ID found: $monitorId. Make sure the monitor is defined before referencing it."
)
