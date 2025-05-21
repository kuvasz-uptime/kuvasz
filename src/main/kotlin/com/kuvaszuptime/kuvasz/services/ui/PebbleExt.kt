package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import jakarta.inject.Singleton
import java.time.OffsetDateTime

@Singleton
class PebbleEngineExtender : BeanCreatedEventListener<PebbleEngine> {
    override fun onCreated(event: BeanCreatedEvent<PebbleEngine>): PebbleEngine {
        val pebbleEngine = event.bean
        pebbleEngine.extensionRegistry.addExtension(PebbleExtension)

        return pebbleEngine
    }
}

object PebbleExtension : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> = mapOf(
        "timeago" to TimeAgoFilter,
        "interval" to IntervalFilter,
    )
}

object TimeAgoFilter : Filter {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun apply(
        input: Any?,
        args: Map<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int
    ): String? =
        if (input != null && input is OffsetDateTime) {
            TimeAgoTransformer.transform(input)
        } else {
            null
        }
}

object IntervalFilter : Filter {
    private const val UNTIL_KEY = "until"

    override fun getArgumentNames(): List<String> = listOf(UNTIL_KEY)

    override fun apply(
        input: Any?,
        args: Map<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int,
    ): String? {
        val until = args?.get(UNTIL_KEY) ?: getCurrentTimestamp()
        return if (input != null && input is OffsetDateTime && until is OffsetDateTime) {
            IntervalTransformer.transform(input, until)
        } else {
            null
        }
    }
}
