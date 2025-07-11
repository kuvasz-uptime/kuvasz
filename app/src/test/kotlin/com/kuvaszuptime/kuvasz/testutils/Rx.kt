package com.kuvaszuptime.kuvasz.testutils

import io.reactivex.rxjava3.subscribers.TestSubscriber

fun <T : Any> T.forwardToSubscriber(testSubscriber: TestSubscriber<T>) = testSubscriber.onNext(this)
