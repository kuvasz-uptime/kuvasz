package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.models.MonitorEvent
import io.reactivex.subscribers.TestSubscriber

fun <T : MonitorEvent> T.toSubscriber(testSubscriber: TestSubscriber<T>) = testSubscriber.onNext(this)
