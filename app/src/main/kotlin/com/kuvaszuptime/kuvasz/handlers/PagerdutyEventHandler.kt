package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsSnapshotRecords
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.PlainTextMessageFormatter
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerPayload
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.PagerdutyAPIClient
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = PagerdutyConfig::class)
class PagerdutyEventHandler(
    eventDispatcher: EventDispatcher,
    private val apiClient: PagerdutyAPIClient,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<PagerdutyEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.PAGERDUTY

    init {
        logger.info("PagerDuty event handler has been initialized")
    }

    private val MaintenanceWindowEvent.deduplicationKey: String
        get() = "kuvasz_maintenance_${window.id}"

    private val UptimeMonitorEvent.deduplicationKey: String
        get() = "kuvasz_uptime_${monitor.id}"

    private val SSLMonitorEvent.deduplicationKey: String
        get() = "kuvasz_ssl_${monitor.id}"

    private val DnsRecordsChangedEvent.deduplicationKey: String
        get() = "kuvasz_dns_drift_${monitor.id}_${currentRecords.contentHash()}"

    private fun DnsSnapshotRecords.contentHash(): String =
        Integer.toHexString(
            entries
                .sortedBy { it.key.name }
                .joinToString(";") { (type, records) -> "${type.name}=${records.joinToString(",")}" }
                .hashCode()
        )

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        val integrationKeys = filterMaintenanceTargets(event).map { (it as PagerdutyConfig).integrationKey }
        when (event) {
            is MaintenanceWindowStartEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = event.toTriggerRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey,
                        severity = PagerdutySeverity.WARNING,
                    )
                    apiClient.triggerAlert(request).handleResponse()
                }

            is MaintenanceWindowEndEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = createResolveRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey,
                    )
                    apiClient.resolveAlert(request).handleResponse()
                }
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        val integrationKeys = filterTargetConfigs(event).map { (it as PagerdutyConfig).integrationKey }
        when (event) {
            is HttpMonitorUpEvent, is PushMonitorUpEvent, is IcmpMonitorUpEvent, is TcpMonitorUpEvent,
            is DnsMonitorUpEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = createResolveRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey
                    )
                    apiClient.resolveAlert(request).handleResponse()
                }

            is HttpMonitorDownEvent, is PushMonitorDownEvent, is IcmpMonitorDownEvent, is TcpMonitorDownEvent,
            is DnsMonitorDownEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = event.toTriggerRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey
                    )
                    apiClient.triggerAlert(request).handleResponse()
                }
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        val integrationKeys = filterTargetConfigs(event).map { (it as PagerdutyConfig).integrationKey }
        when (event) {
            is SSLValidEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = createResolveRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey
                    )
                    apiClient.resolveAlert(request).handleResponse()
                }

            is SSLInvalidEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = event.toTriggerRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey
                    )
                    apiClient.triggerAlert(request).handleResponse()
                }

            is SSLWillExpireEvent ->
                integrationKeys.forEach { integrationKey ->
                    val request = event.toTriggerRequest(
                        serviceKey = integrationKey,
                        deduplicationKey = event.deduplicationKey,
                        severity = PagerdutySeverity.WARNING
                    )
                    apiClient.triggerAlert(request).handleResponse()
                }
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        val integrationKeys = filterTargetConfigs(event).map { (it as PagerdutyConfig).integrationKey }
        integrationKeys.forEach { integrationKey ->
            val request = event.toTriggerRequest(
                serviceKey = integrationKey,
                deduplicationKey = event.deduplicationKey,
                severity = PagerdutySeverity.WARNING,
            )
            apiClient.triggerAlert(request).handleResponse()
        }
    }

    private fun MaintenanceWindowEvent.toTriggerRequest(
        serviceKey: String,
        deduplicationKey: String,
        severity: PagerdutySeverity,
    ) =
        PagerdutyTriggerRequest(
            routingKey = serviceKey,
            dedupKey = deduplicationKey,
            payload = PagerdutyTriggerPayload(
                summary = PlainTextMessageFormatter.toFormattedMessage(this),
                source = window.name,
                severity = severity
            )
        )

    private fun MonitorEvent<*>.toTriggerRequest(
        serviceKey: String,
        deduplicationKey: String,
        severity: PagerdutySeverity = PagerdutySeverity.CRITICAL
    ) =
        PagerdutyTriggerRequest(
            routingKey = serviceKey,
            dedupKey = deduplicationKey,
            payload = PagerdutyTriggerPayload(
                summary = toStructuredMessage().summary,
                source = monitor.name,
                severity = severity
            )
        )

    private fun createResolveRequest(serviceKey: String, deduplicationKey: String) =
        PagerdutyResolveRequest(
            routingKey = serviceKey,
            dedupKey = deduplicationKey
        )
}
