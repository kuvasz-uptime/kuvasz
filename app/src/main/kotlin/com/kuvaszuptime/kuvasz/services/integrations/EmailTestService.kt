package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import io.micronaut.context.annotation.Requires
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import org.simplejavamail.email.EmailBuilder

@Singleton
@Requires(beans = [SMTPMailerConfig::class, EmailNotificationConfig::class])
class EmailTestService(private val smtpMailer: SMTPMailer) : TestableNotificationService<EmailNotificationConfig> {

    override fun sendTestMessage(integrationConfig: EmailNotificationConfig): Single<NotificationTestResult> {
        val testEmail = EmailBuilder
            .startingBlank()
            .to(integrationConfig.toAddress, integrationConfig.toAddress)
            .from(integrationConfig.fromAddress, integrationConfig.fromAddress)
            .withSubject(Messages.integrationTestMessage())
            .withPlainText(Messages.integrationTestMessage())
            .buildEmail()

        return Completable.fromFuture(smtpMailer.sendAsync(testEmail))
            .andThen(Single.just("OK"))
            .onErrorResumeNext { ex -> Single.error(ex) }
            .toNotificationTestResult()
    }
}
