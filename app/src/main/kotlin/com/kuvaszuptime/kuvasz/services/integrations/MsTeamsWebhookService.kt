package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.MsTeamsCardFactory
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsMessage
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsNotificationConfig
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import java.net.URI

@Singleton
@Requires(bean = MsTeamsNotificationConfig::class)
class MsTeamsWebhookClient(@param:Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(webhookUrl: URI, message: MsTeamsMessage): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        // The workflow's trigger answers with an empty 202, so the response body can't be retrieved
        return Single.fromPublisher(client.exchange(req)).map { "OK" }
    }
}

@Singleton
@Requires(bean = MsTeamsWebhookClient::class)
class MsTeamsWebhookService(
    private val client: MsTeamsWebhookClient,
    private val cardFactory: MsTeamsCardFactory,
) : TestableNotificationService<MsTeamsNotificationConfig> {

    fun sendMessage(integrationConfig: IntegrationConfig, message: MsTeamsMessage): Single<String> {
        val webhookUrl = (integrationConfig as MsTeamsNotificationConfig).webhookUrl.toUri()
        return client.sendMessage(webhookUrl, message)
    }

    fun sendEvent(integrationConfig: IntegrationConfig, event: UptimeMonitorEvent): Single<String> =
        sendMessage(integrationConfig, cardFactory.fromUptimeEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: SSLMonitorEvent): Single<String> =
        sendMessage(integrationConfig, cardFactory.fromSSLEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: DnsRecordsChangedEvent): Single<String> =
        sendMessage(integrationConfig, cardFactory.fromDnsRecordsChangedEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: MaintenanceWindowEvent): Single<String> =
        sendMessage(integrationConfig, cardFactory.fromMaintenanceEvent(event))

    override fun sendTestMessage(integrationConfig: MsTeamsNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, cardFactory.testMessage()).toNotificationTestResult()
}
