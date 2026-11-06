package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import io.micronaut.context.annotation.Requires
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton

@Singleton
@Requires(beans = [SMTPMailerConfig::class, EmailNotificationConfig::class])
class EmailTestService(private val smtpMailer: SMTPMailer) : TestableNotificationService<EmailNotificationConfig> {

    override fun sendTestMessage(integrationConfig: EmailNotificationConfig): Single<NotificationTestResult> {
        val testEmails = EmailFactory(integrationConfig).testEmails()

        return Completable
            .merge(testEmails.map { email -> Completable.fromFuture(smtpMailer.sendAsync(email)) })
            .andThen(Single.just("OK"))
            .onErrorResumeNext { ex -> Single.error(ex) }
            .toNotificationTestResult()
    }
}
