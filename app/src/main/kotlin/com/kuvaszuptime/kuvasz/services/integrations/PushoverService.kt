package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.PushoverMessageFactory
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.PushoverCancelRequest
import com.kuvaszuptime.kuvasz.models.handlers.PushoverMessage
import com.kuvaszuptime.kuvasz.models.handlers.PushoverNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.PushoverPriority
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton

@Singleton
@Requires(bean = PushoverNotificationConfig::class)
class PushoverClient(@param:Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(message: PushoverMessage): Single<String> =
        Single.fromPublisher(client.retrieve(HttpRequest.POST(MESSAGES_URL.toUri(), message), String::class.java))

    /**
     * Calls off every outstanding emergency notification that was sent with the given tag, so that a recovered
     * monitor stops nagging the recipient without them having to acknowledge it.
     **/
    @Retryable
    fun cancelEmergency(token: String, tag: String): Single<String> =
        Single.fromPublisher(
            client.retrieve(
                HttpRequest.POST("$CANCEL_BY_TAG_URL/$tag.json".toUri(), PushoverCancelRequest(token)),
                String::class.java,
            )
        )

    companion object {
        private const val MESSAGES_URL = "https://api.pushover.net/1/messages.json"
        private const val CANCEL_BY_TAG_URL = "https://api.pushover.net/1/receipts/cancel_by_tag"
    }
}

@Singleton
@Requires(bean = PushoverClient::class)
class PushoverService(
    private val client: PushoverClient,
    private val messageFactory: PushoverMessageFactory,
) : TestableNotificationService<PushoverNotificationConfig> {

    fun sendEvent(integrationConfig: IntegrationConfig, event: UptimeMonitorEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromUptimeEvent(event), event.emergencyTag)

    fun sendEvent(integrationConfig: IntegrationConfig, event: SSLMonitorEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromSSLEvent(event), event.emergencyTag)

    fun sendEvent(integrationConfig: IntegrationConfig, event: DnsRecordsChangedEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromDnsRecordsChangedEvent(event))

    fun sendEvent(integrationConfig: IntegrationConfig, event: MaintenanceWindowEvent): Single<String> =
        sendMessage(integrationConfig, messageFactory.fromMaintenanceEvent(event))

    override fun sendTestMessage(integrationConfig: PushoverNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, messageFactory.testMessage()).toNotificationTestResult()

    /**
     * Cancels the outstanding emergency notifications of a recovered monitor, or returns null if the given
     * integration never escalates anything in the first place, so there is nothing to call off.
     **/
    fun cancelEmergency(integrationConfig: IntegrationConfig, event: UptimeMonitorEvent): Single<String>? =
        cancelEmergency(integrationConfig, event.emergencyTag)

    fun cancelEmergency(integrationConfig: IntegrationConfig, event: SSLMonitorEvent): Single<String>? =
        cancelEmergency(integrationConfig, event.emergencyTag)

    private fun sendMessage(
        integrationConfig: IntegrationConfig,
        message: PushoverMessage,
        emergencyTag: String? = null,
    ): Single<String> {
        val pushoverConfig = integrationConfig as PushoverNotificationConfig
        // Only an event that can be resolved later is escalated: without a tag there would be no way to call the
        // notification off, and it would keep repeating until it expires
        val emergency = pushoverConfig.emergencyEnabled &&
            message.priority == PushoverPriority.HIGH &&
            emergencyTag != null

        return client.sendMessage(
            message.copy(
                token = pushoverConfig.apiToken,
                user = pushoverConfig.userKey,
                device = pushoverConfig.device,
                sound = pushoverConfig.sound,
                priority = if (emergency) PushoverPriority.EMERGENCY else message.priority,
                retry = pushoverConfig.emergencyRetrySeconds.takeIf { emergency },
                expire = pushoverConfig.emergencyExpireSeconds.takeIf { emergency },
                tags = emergencyTag?.takeIf { emergency },
            )
        )
    }

    private fun cancelEmergency(integrationConfig: IntegrationConfig, tag: String): Single<String>? =
        (integrationConfig as PushoverNotificationConfig)
            .takeIf { it.emergencyEnabled }
            ?.let { client.cancelEmergency(token = it.apiToken, tag = tag) }

    // The tags identify an emergency notification without Kuvasz ever storing the receipt Pushover hands back:
    // they are recomputed from the very same monitor when its recovery arrives
    private val UptimeMonitorEvent.emergencyTag: String
        get() = "kuvasz_uptime_${monitor.id}"

    private val SSLMonitorEvent.emergencyTag: String
        get() = "kuvasz_ssl_${monitor.id}"
}
