package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
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

    override fun filterTargetConfigs(event: MonitorEvent<*>): Set<IntegrationConfig> =
        integrationRepository
            .getEffectiveIntegrations(event.monitor.integrations, integrationType)
            .filterByEventType(event.toIntegrationEventType())

    fun filterMaintenanceTargets(event: MaintenanceWindowEvent): Set<IntegrationConfig> =
        integrationRepository
            .getExplicitlyAssignedIntegrations(event.window.integrations, integrationType)
            .filterByEventType(event.toIntegrationEventType())
}

private fun Set<IntegrationConfig>.filterByEventType(type: IntegrationEventType): Set<IntegrationConfig> =
    filter { config ->
        val excludedEvents = config.excludedEvents.orEmpty()
        !excludedEvents.contains(type)
    }.toSet()

@Suppress("NotImplementedDeclaration")
fun MonitorEvent<*>.toIntegrationEventType() = when (this) {
    is SSLInvalidEvent -> IntegrationEventType.SSL_INVALID
    is SSLValidEvent -> IntegrationEventType.SSL_VALID
    is SSLWillExpireEvent -> IntegrationEventType.SSL_WILL_EXPIRE
    is HttpMonitorDownEvent -> IntegrationEventType.HTTP_DOWN
    is HttpMonitorUpEvent -> IntegrationEventType.HTTP_UP
    is PushMonitorDownEvent -> IntegrationEventType.PUSH_DOWN
    is PushMonitorUpEvent -> IntegrationEventType.PUSH_UP
    is IcmpMonitorDownEvent -> IntegrationEventType.ICMP_DOWN
    is IcmpMonitorUpEvent -> IntegrationEventType.ICMP_UP
    is HttpRedirectEvent ->
        throw NotImplementedError("Redirect events are not supported in integrations")
}

fun MaintenanceWindowEvent.toIntegrationEventType() = when (this) {
    is MaintenanceWindowStartEvent -> IntegrationEventType.MAINTENANCE_START
    is MaintenanceWindowEndEvent -> IntegrationEventType.MAINTENANCE_END
}
