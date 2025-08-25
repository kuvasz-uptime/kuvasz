package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.UptimeCheckException
import com.kuvaszuptime.kuvasz.models.dto.toJsonNode
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.reactivex.rxjava3.subscribers.TestSubscriber
import kotlin.time.Duration.Companion.seconds

fun EventDispatcher.upSubscriber(): TestSubscriber<HttpMonitorUpEvent> {
    val subscriber = TestSubscriber<HttpMonitorUpEvent>()
    this.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun EventDispatcher.downSubscriber(): TestSubscriber<HttpMonitorDownEvent> {
    val subscriber = TestSubscriber<HttpMonitorDownEvent>()
    this.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun EventDispatcher.redirectSubscriber(): TestSubscriber<HttpRedirectEvent> {
    val subscriber = TestSubscriber<HttpRedirectEvent>()
    this.subscribeToHttpRedirectEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun mockMonitor(
    expectedKeyword: String? = null,
    expectedKeywordCaseSensitive: Boolean = false,
    expectedKeywordNegated: Boolean = false,
    responseTimeThreshold: Int? = null,
    expectedStatusCodes: Set<Int> = emptySet(),
    followRedirects: Boolean = true,
    expectedHeaders: Map<String, String> = emptyMap(),
): HttpMonitorRecord = HttpMonitorRecord().apply {
    this.id = 1L
    this.url = "http://example.com"
    this.expectedKeyword = expectedKeyword
    this.expectedKeywordCaseSensitive = expectedKeywordCaseSensitive
    this.expectedKeywordNegated = expectedKeywordNegated
    this.responseTimeThresholdMillis = responseTimeThreshold
    this.expectedStatusCodes = expectedStatusCodes.toTypedArray()
    this.followRedirects = followRedirects
    this.expectedHeaders = expectedHeaders.toJsonNode()
}

suspend inline fun <reified E : UptimeCheckException> TestSubscriber<HttpMonitorDownEvent>.assertSingleError(
    expectedMessage: String,
) {
    this.awaitCount(1)
    val event = eventually(2.seconds) { this.values().first() }
    event.error.shouldBeInstanceOf<E>()
    event.error.message shouldStartWith expectedMessage
}

suspend inline fun TestSubscriber<HttpRedirectEvent>.assertSingleValue(monitorId: Long, redirectLocation: String) {
    this.awaitCount(1)
    val event = eventually(2.seconds) { this.values().first() }
    event.monitor.id shouldBe monitorId
    event.redirectLocation shouldBe redirectLocation.toUri()
}
