package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import io.reactivex.rxjava3.subscribers.TestSubscriber

fun <T : MonitorEvent> T.toSubscriber(testSubscriber: TestSubscriber<T>) = testSubscriber.onNext(this)
