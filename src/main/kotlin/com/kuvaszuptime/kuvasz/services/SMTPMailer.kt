package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import org.simplejavamail.api.email.Email
import org.simplejavamail.api.mailer.AsyncResponse
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMTPMailer @Inject constructor(smtpMailerConfig: SMTPMailerConfig) {

    companion object {
        private val logger = LoggerFactory.getLogger(SMTPMailer::class.java)
    }

    private val mailerClient =
        MailerBuilder
            .withTransportStrategy(smtpMailerConfig.transportStrategy.toJavaMailerTransportStrategy())
            .withSMTPServerHost(smtpMailerConfig.host)
            .withSMTPServerPort(smtpMailerConfig.port)
            .apply {
                if (!smtpMailerConfig.username.isNullOrBlank() && !smtpMailerConfig.password.isNullOrBlank()) {
                    withSMTPServerUsername(smtpMailerConfig.username)
                        .withSMTPServerPassword(smtpMailerConfig.password)
                }
            }.buildMailer()

    init {
        @Suppress("TooGenericExceptionCaught")
        try {
            mailerClient.testConnection()
            logger.info("SMTP connection to ${smtpMailerConfig.host} has been set up successfully")
        } catch (e: Throwable) {
            logger.error("Connection to ${smtpMailerConfig.host} cannot be set up")
            throw e
        }
    }

    fun sendAsync(email: Email): AsyncResponse? = mailerClient.sendMail(email, true)
}
