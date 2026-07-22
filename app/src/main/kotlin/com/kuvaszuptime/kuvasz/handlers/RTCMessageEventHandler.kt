package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.RichTextMessageFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.TextMessageService

abstract class RTCMessageEventHandler(
    eventDispatcher: EventDispatcher,
    private val messageService: TextMessageService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    internal abstract val formatter: RichTextMessageFormatter

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        val message = formatter.toFormattedMessage(event)
        filterMaintenanceTargets(event).forEach { target ->
            messageService.sendMessage(target, message).handleResponse()
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        val message = formatter.toFormattedMessage(event)
        filterTargetConfigs(event).forEach { target ->
            messageService.sendMessage(target, message).handleResponse()
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        val message = formatter.toFormattedMessage(event)
        filterTargetConfigs(event).forEach { target ->
            messageService.sendMessage(target, message).handleResponse()
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        val message = formatter.toFormattedMessage(event)
        filterTargetConfigs(event).forEach { target ->
            messageService.sendMessage(target, message).handleResponse()
        }
    }
}
