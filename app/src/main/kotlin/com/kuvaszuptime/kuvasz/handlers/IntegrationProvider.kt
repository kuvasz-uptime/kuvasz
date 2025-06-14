package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.IntegrationRepository

interface IntegrationProvider {
    val integrationType: IntegrationType

    fun filterTargetConfigs(monitorConfigs: Array<IntegrationID>): Set<IntegrationConfig>
}

abstract class AbstractIntegrationProvider(
    private val integrationRepository: IntegrationRepository,
) : IntegrationProvider {

    override fun filterTargetConfigs(monitorConfigs: Array<IntegrationID>): Set<IntegrationConfig> =
        integrationRepository.getEnabledIntegrations(monitorConfigs, integrationType)
}
