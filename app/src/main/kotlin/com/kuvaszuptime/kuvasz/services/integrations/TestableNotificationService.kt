package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.reactivex.rxjava3.core.Single

private val logger = loggerFor<TestableNotificationService<*>>()

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

internal fun Single<*>.toNotificationTestResult(): Single<NotificationTestResult> {
    return this
        .map { NotificationTestResult.success() }
        .onErrorReturn { error ->
            val errorDetails = error.message ?: error.toString()
            logger.error("Failed to send test notification: $errorDetails")
            NotificationTestResult.failure(errorDetails)
        }
}
