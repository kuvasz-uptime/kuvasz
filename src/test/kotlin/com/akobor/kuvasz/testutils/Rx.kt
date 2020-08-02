package com.akobor.kuvasz.testutils

import com.akobor.kuvasz.events.Event
import io.reactivex.subscribers.TestSubscriber

fun <T : Event> T.toSubscriber(testSubscriber: TestSubscriber<T>) = testSubscriber.onNext(this)
