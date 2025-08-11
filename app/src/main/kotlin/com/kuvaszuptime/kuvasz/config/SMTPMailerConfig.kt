package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.simplejavamail.api.mailer.config.TransportStrategy as JavaMailerTransportStrategy

@Requires(property = SMTPMailerConfig.CONFIG_PREFIX)
@ConfigurationProperties(SMTPMailerConfig.CONFIG_PREFIX)
@Singleton
@Introspected
class SMTPMailerConfig {

    @field:NotBlank(message = ValidationMessages.SMTP_HOST_NOT_BLANK)
    var host: String? = null

    @field:NotNull(message = ValidationMessages.SMTP_PORT_NOT_NULL)
    var port: Int? = null

    var username: String? = null

    var password: String? = null

    var transportStrategy: TransportStrategy = TransportStrategy.SMTP_TLS

    companion object {
        const val CONFIG_PREFIX = "smtp-config"
    }
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
