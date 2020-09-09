package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import javax.inject.Singleton

@Singleton
class EventDispatcher {

    private val monitorUpEvents = PublishSubject.create<MonitorUpEvent>()
    private val monitorDownEvents = PublishSubject.create<MonitorDownEvent>()
    private val redirectEvents = PublishSubject.create<RedirectEvent>()
    private val sslValidEvents = PublishSubject.create<SSLValidEvent>()
    private val sslWillExpireEvents = PublishSubject.create<SSLWillExpireEvent>()
    private val sslInvalidEvents = PublishSubject.create<SSLInvalidEvent>()

    fun dispatch(event: MonitorEvent) =
        when (event) {
            is MonitorUpEvent -> monitorUpEvents.onNext(event)
            is MonitorDownEvent -> monitorDownEvents.onNext(event)
            is RedirectEvent -> redirectEvents.onNext(event)
            is SSLValidEvent -> sslValidEvents.onNext(event)
            is SSLInvalidEvent -> sslInvalidEvents.onNext(event)
            is SSLWillExpireEvent -> sslWillExpireEvents.onNext(event)
        }

    fun subscribeToMonitorUpEvents(consumer: (MonitorUpEvent) -> Unit): Disposable =
        monitorUpEvents.subscribe(consumer)

    fun subscribeToMonitorDownEvents(consumer: (MonitorDownEvent) -> Unit): Disposable =
        monitorDownEvents.subscribe(consumer)

    fun subscribeToRedirectEvents(consumer: (RedirectEvent) -> Unit): Disposable =
        redirectEvents.subscribe(consumer)

    fun subscribeToSSLValidEvents(consumer: (SSLValidEvent) -> Unit): Disposable =
        sslValidEvents.subscribe(consumer)

    fun subscribeToSSLInvalidEvents(consumer: (SSLInvalidEvent) -> Unit): Disposable =
        sslInvalidEvents.subscribe(consumer)

    fun subscribeToSSLWillExpireEvents(consumer: (SSLWillExpireEvent) -> Unit): Disposable =
        sslWillExpireEvents.subscribe(consumer)
}
