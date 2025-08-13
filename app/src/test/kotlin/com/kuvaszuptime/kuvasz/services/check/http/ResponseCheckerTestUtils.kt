package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.UptimeCheckException
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.reactivex.rxjava3.subscribers.TestSubscriber

fun EventDispatcher.upSubscriber(): TestSubscriber<MonitorUpEvent> {
    val subscriber = TestSubscriber<MonitorUpEvent>()
    this.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun EventDispatcher.downSubscriber(): TestSubscriber<MonitorDownEvent> {
    val subscriber = TestSubscriber<MonitorDownEvent>()
    this.subscribeToMonitorDownEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun EventDispatcher.redirectSubscriber(): TestSubscriber<RedirectEvent> {
    val subscriber = TestSubscriber<RedirectEvent>()
    this.subscribeToRedirectEvents { it.forwardToSubscriber(subscriber) }
    return subscriber
}

fun mockMonitor(
    expectedKeyword: String? = null,
    expectedKeywordCaseSensitive: Boolean = false,
    expectedKeywordNegated: Boolean = false,
    responseTimeThreshold: Int? = null,
    expectedStatusCodes: Set<Int> = emptySet(),
    followRedirects: Boolean = true,
): MonitorRecord = MonitorRecord().apply {
    this.id = 1L
    this.url = "http://example.com"
    this.expectedKeyword = expectedKeyword
    this.expectedKeywordCaseSensitive = expectedKeywordCaseSensitive
    this.expectedKeywordNegated = expectedKeywordNegated
    this.responseTimeThresholdMillis = responseTimeThreshold
    this.expectedStatusCodes = expectedStatusCodes.toTypedArray()
    this.followRedirects = followRedirects
}

inline fun <reified E : UptimeCheckException> TestSubscriber<MonitorDownEvent>.assertSingleError(
    expectedMessage: String,
) {
    val event = this.awaitCount(1).values().first()
    event.error.shouldBeInstanceOf<E>()
    event.error.message shouldStartWith expectedMessage
}

inline fun TestSubscriber<RedirectEvent>.assertSingleValue(monitorId: Long, redirectLocation: String) {
    val event = this.awaitCount(1).values().first()
    event.monitor.id shouldBe monitorId
    event.redirectLocation shouldBe redirectLocation.toUri()
}
