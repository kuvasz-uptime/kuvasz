package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import io.reactivex.rxjava3.core.Single
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(TestableNotificationService::class.java)

interface TestableNotificationService<T : IntegrationConfig> {
    fun sendTestMessage(integrationConfig: T): Single<NotificationTestResult>
}

data class NotificationTestResult(
    val success: Boolean,
    val message: String,
) {
    companion object {
        fun success() = NotificationTestResult(true, Messages.successfulTestResultMessage())
        fun failure(message: String) = NotificationTestResult(false, Messages.failedTestResultMessage(message))
    }
}

internal fun Single<String>.toNotificationTestResult(): Single<NotificationTestResult> {
    return this
        .map { NotificationTestResult.success() }
        .onErrorReturn { error ->
            val errorDetails = error.message ?: error.toString()
            logger.error("Failed to send test notification: $errorDetails")
            NotificationTestResult.failure(errorDetails)
        }
}
