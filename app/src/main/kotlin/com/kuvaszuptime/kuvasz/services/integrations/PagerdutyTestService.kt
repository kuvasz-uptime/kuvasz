package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.pagerduty.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.pagerduty.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.pagerduty.PagerdutyTriggerPayload
import com.kuvaszuptime.kuvasz.models.handlers.pagerduty.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.util.AppInfo
import io.micronaut.context.annotation.Requires
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton

@Singleton
@Requires(bean = PagerdutyConfig::class)
class PagerdutyTestService(private val pagerdutyAPIClient: PagerdutyAPIClient) :
    TestableNotificationService<PagerdutyConfig> {

    override fun sendTestMessage(integrationConfig: PagerdutyConfig): Single<NotificationTestResult> {
        val testDedupKey = "kuvasz-test"
        val testTriggerRequest = PagerdutyTriggerRequest(
            routingKey = integrationConfig.integrationKey,
            dedupKey = testDedupKey,
            payload = PagerdutyTriggerPayload(
                summary = Messages.integrationTestMessage(),
                source = AppInfo.NAME,
                severity = PagerdutySeverity.WARNING,
            )
        )
        val testResolveRequest = PagerdutyResolveRequest(
            routingKey = integrationConfig.integrationKey,
            dedupKey = testDedupKey,
        )

        // Send a trigger followed by a resolve to avoid leaving a test incident open in PagerDuty
        return pagerdutyAPIClient
            .triggerAlert(testTriggerRequest)
            .flatMap { pagerdutyAPIClient.resolveAlert(testResolveRequest) }
            .toNotificationTestResult()
    }
}
