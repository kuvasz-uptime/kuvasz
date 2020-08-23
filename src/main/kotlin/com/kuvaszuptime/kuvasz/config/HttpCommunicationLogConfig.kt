package com.kuvaszuptime.kuvasz.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Introspected
import io.micronaut.logging.LogLevel

@ConfigurationProperties("http-communication-log")
@Context
@Introspected
class HttpCommunicationLogConfig {
    var level: LogLevel = LogLevel.INFO
}
