package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.AppriseMessageFactory
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.AppriseMessage
import com.kuvaszuptime.kuvasz.models.handlers.AppriseNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.util.getBodyAs
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
@Requires(bean = AppriseNotificationConfig::class)
class AppriseClient(@param:Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(url: URI, headers: Map<String, String>, message: AppriseMessage): Single<String> {
        val req = HttpRequest.POST(url, message)
        headers.forEach { (name, value) -> req.header(name, value) }
        // Apprise answers with an empty 204 when none of its endpoints matched the tag
        return Single.fromPublisher(client.exchange(req, String::class.java)).map { it.getBodyAs<String>() ?: "OK" }
    }
}

@Singleton
@Requires(bean = AppriseClient::class)
class AppriseService(
    private val client: AppriseClient,
    private val messageFactory: AppriseMessageFactory,
) : TestableNotificationService<AppriseNotificationConfig> {

    fun sendMessage(integrationConfig: IntegrationConfig, message: AppriseMessage): Single<String> {
        val appriseConfig = integrationConfig as AppriseNotificationConfig
        return client.sendMessage(
            url = appriseConfig.url.toUri(),
            headers = appriseConfig.requestHeaders.orEmpty(),
            message = message.copy(tag = appriseConfig.tag, urls = appriseConfig.targetUrls),
        )
    }

    fun sendEvent(integrationConfig: IntegrationConfig, event: UptimeMonitorEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromUptimeEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: SSLMonitorEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromSSLEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: DnsRecordsChangedEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromDnsRecordsChangedEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: MaintenanceWindowEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromMaintenanceEvent(event))

    override fun sendTestMessage(integrationConfig: AppriseNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, messageFactory.testMessage()).toNotificationTestResult()
}
