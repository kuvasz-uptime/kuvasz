package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.monitor.InvalidMonitorIdException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import jakarta.inject.Singleton

@Singleton
class MonitorIdValidator(private val sharedMonitorRepository: SharedMonitorRepository) {

    /**
     * Validates an array of monitor IDs against the existing monitors.
     */
    fun validateMonitorIds(ids: Array<MonitorID>): List<MonitorID> = ids.toList().onlyExisting()

    /**
     * Validates a list of monitor IDs against the existing monitors and returns a set of valid,
     * existing monitor IDs.
     *
     * @return a set of valid monitor IDs.
     * @throws InvalidMonitorIdException if any of the provided IDs are not valid.
     */
    fun validateMonitorIds(rawIds: List<String>): Set<MonitorID> =
        rawIds.map { id -> MonitorID.fromString(id) ?: throw InvalidMonitorIdException(id) }.onlyExisting().toSet()

    // Looked up in a single query, whatever the number of the monitors
    private fun List<MonitorID>.onlyExisting(): List<MonitorID> {
        val existing = sharedMonitorRepository.fetchNumericIds(this).keys
        return filter { it in existing }
    }
}

data class ResolvedMonitorIds(val valid: Set<MonitorID>, val ignored: List<String>)
