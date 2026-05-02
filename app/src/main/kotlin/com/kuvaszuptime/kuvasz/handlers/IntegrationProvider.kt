package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository

interface IntegrationProvider {
    val integrationType: IntegrationType

    fun filterTargetConfigs(event: MonitorEvent<*>): Set<IntegrationConfig>
}

abstract class AbstractIntegrationProvider(
    private val integrationRepository: IntegrationRepository,
) : IntegrationProvider {

    override fun filterTargetConfigs(event: MonitorEvent<*>): Set<IntegrationConfig> = integrationRepository
        .getEnabledIntegrations(event.monitor.integrations, integrationType)
        .filterByEventType(event)

    @Suppress("NotImplementedDeclaration")
    private fun Set<IntegrationConfig>.filterByEventType(event: MonitorEvent<*>): Set<IntegrationConfig> =
        filter { config ->
            when (event) {
                is SSLInvalidEvent -> config.supportsEventType(IntegrationEventType.SSL_INVALID)
                is SSLValidEvent -> config.supportsEventType(IntegrationEventType.SSL_VALID)
                is SSLWillExpireEvent -> config.supportsEventType(IntegrationEventType.SSL_WILL_EXPIRE)
                is HttpMonitorDownEvent -> config.supportsEventType(IntegrationEventType.HTTP_DOWN)
                is HttpMonitorUpEvent -> config.supportsEventType(IntegrationEventType.HTTP_UP)
                is PushMonitorDownEvent -> config.supportsEventType(IntegrationEventType.PUSH_DOWN)
                is PushMonitorUpEvent -> config.supportsEventType(IntegrationEventType.PUSH_UP)
                is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in integrations")
            }
        }.toSet()

    private fun IntegrationConfig.supportsEventType(eventType: IntegrationEventType) =
        !excludedEventTypes.orEmpty().contains(eventType)
}
