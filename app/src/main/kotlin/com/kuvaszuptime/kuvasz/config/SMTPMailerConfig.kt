package com.kuvaszuptime.kuvasz.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.simplejavamail.api.mailer.config.TransportStrategy as JavaMailerTransportStrategy

@ConfigurationProperties("smtp-config")
@Singleton
@Introspected
class SMTPMailerConfig {

    @NotBlank
    var host: String? = null

    @NotNull
    var port: Int? = null

    var username: String? = null

    var password: String? = null

    var transportStrategy: TransportStrategy = TransportStrategy.SMTP_TLS
}

enum class TransportStrategy {
    SMTP_TLS {
        override fun toJavaMailerTransportStrategy() = JavaMailerTransportStrategy.SMTP_TLS
    },
    SMTP {
        override fun toJavaMailerTransportStrategy() = JavaMailerTransportStrategy.SMTP
    },
    SMTPS {
        override fun toJavaMailerTransportStrategy() = JavaMailerTransportStrategy.SMTPS
    };

    abstract fun toJavaMailerTransportStrategy(): JavaMailerTransportStrategy
}
