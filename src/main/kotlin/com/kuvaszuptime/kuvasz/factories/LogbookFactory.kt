package com.kuvaszuptime.kuvasz.factories

import io.micronaut.context.annotation.Factory
import org.zalando.logbook.DefaultHttpLogWriter
import org.zalando.logbook.DefaultSink
import org.zalando.logbook.Logbook
import org.zalando.logbook.SecurityStrategy
import org.zalando.logbook.WithoutBodyStrategy
import org.zalando.logbook.json.JsonHttpLogFormatter
import javax.inject.Singleton

@Factory
class LogbookFactory {

    @Singleton
    fun logbook(): Logbook =
        Logbook.builder()
            .strategy(SecurityStrategy())
            .strategy(WithoutBodyStrategy())
            .sink(DefaultSink(JsonHttpLogFormatter(), DefaultHttpLogWriter()))
            .build()
}
