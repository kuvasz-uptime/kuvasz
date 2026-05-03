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

    override fun filterTargetConfigs(event: MonitorEvent<*>): Set<IntegrationConfig> {
        return integrationRepository
            .getEnabledIntegrations(event.monitor.integrations, integrationType)
            .filter { config ->
                val excludedEvents = config.excludedEvents.orEmpty()
                when (event) {
                    is SSLInvalidEvent -> !excludedEvents.contains(IntegrationEventType.SSL_INVALID)
                    is SSLValidEvent -> !excludedEvents.contains(IntegrationEventType.SSL_VALID)
                    is SSLWillExpireEvent -> !excludedEvents.contains(IntegrationEventType.SSL_WILL_EXPIRE)
                    is HttpMonitorDownEvent -> !excludedEvents.contains(IntegrationEventType.HTTP_DOWN)
                    is HttpMonitorUpEvent -> !excludedEvents.contains(IntegrationEventType.HTTP_UP)
                    is PushMonitorDownEvent -> !excludedEvents.contains(IntegrationEventType.PUSH_DOWN)
                    is PushMonitorUpEvent -> !excludedEvents.contains(IntegrationEventType.PUSH_UP)
                    is HttpRedirectEvent ->
                        throw NotImplementedError("Redirect events are not supported in integrations")
                }
            }.toSet()
    }
}
