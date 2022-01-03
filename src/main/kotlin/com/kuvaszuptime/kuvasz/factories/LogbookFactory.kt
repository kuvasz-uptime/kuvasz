package com.kuvaszuptime.kuvasz.factories

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.zalando.logbook.Conditions.exclude
import org.zalando.logbook.Conditions.requestTo
import org.zalando.logbook.DefaultHttpLogWriter
import org.zalando.logbook.DefaultSink
import org.zalando.logbook.Logbook
import org.zalando.logbook.SecurityStrategy
import org.zalando.logbook.WithoutBodyStrategy
import org.zalando.logbook.json.JsonHttpLogFormatter

@Factory
class LogbookFactory {

    @Singleton
    fun logbook(): Logbook =
        Logbook.builder()
            .condition(exclude(requestTo("/health")))
            .strategy(SecurityStrategy())
            .strategy(WithoutBodyStrategy())
            .sink(DefaultSink(JsonHttpLogFormatter(), DefaultHttpLogWriter()))
            .build()
}
