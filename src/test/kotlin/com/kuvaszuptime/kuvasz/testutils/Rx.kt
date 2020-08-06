package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.models.Event
import io.reactivex.subscribers.TestSubscriber

fun <T : Event> T.toSubscriber(testSubscriber: TestSubscriber<T>) = testSubscriber.onNext(this)
