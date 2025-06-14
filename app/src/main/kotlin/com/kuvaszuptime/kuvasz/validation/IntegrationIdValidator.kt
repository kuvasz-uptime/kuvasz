package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.InvalidIntegrationIDException
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import jakarta.inject.Singleton

@Singleton
class IntegrationIdValidator(private val integrationRepository: IntegrationRepository) {

    private fun IntegrationID.checkIfConfigured(): IntegrationID {
        if (!integrationRepository.configuredIntegrations.contains(this)) {
            throw NonExistingIntegrationIdException("Non-existing integration ID found: $this.")
        }
        return this
    }

    /**
     * Validates an array of integration IDs against the configured integrations.
     *
     * @throws NonExistingIntegrationIdException if any of the provided IDs are not configured.
     */
    fun validateIntegrationIds(ids: Array<IntegrationID>) = ids.forEach { id -> id.checkIfConfigured() }

    /**
     * Validates a list of integration IDs against the configured integrations.
     *
     * @return a set of valid integration IDs.
     * @throws NonExistingIntegrationIdException if any of the provided IDs are not configured.
     */
    fun validateIntegrationIds(rawIds: List<String>): Set<IntegrationID> = rawIds.map { id ->
        IntegrationID.fromString(id)?.checkIfConfigured() ?: throw InvalidIntegrationIDException(id)
    }.toSet()
}

class NonExistingIntegrationIdException(message: String) : RuntimeException(message)
