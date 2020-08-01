package com.akobor.kuvasz.services

import com.akobor.kuvasz.events.Event
import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.events.RedirectEvent
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import javax.inject.Singleton

@Singleton
class EventDispatcher {

    private val monitorUpEvents = PublishSubject.create<MonitorUpEvent>()
    private val monitorDownEvents = PublishSubject.create<MonitorDownEvent>()
    private val redirectEvents = PublishSubject.create<RedirectEvent>()

    fun dispatch(event: Event) =
        when (event) {
            is MonitorUpEvent -> monitorUpEvents.onNext(event)
            is MonitorDownEvent -> monitorDownEvents.onNext(event)
            is RedirectEvent -> redirectEvents.onNext(event)
        }

    fun subscribeToMonitorUpEvents(consumer: (MonitorUpEvent) -> Unit): Disposable =
        monitorUpEvents.subscribe(consumer)

    fun subscribeToMonitorDownEvents(consumer: (MonitorDownEvent) -> Unit): Disposable =
        monitorDownEvents.subscribe(consumer)

    fun subscribeToRedirectEvents(consumer: (RedirectEvent) -> Unit): Disposable =
        redirectEvents.subscribe(consumer)

    fun subscribeToMonitorUpEvents(observer: Observer<MonitorUpEvent>) =
        monitorUpEvents.subscribe(observer)

    fun subscribeToMonitorDownEvents(observer: Observer<MonitorDownEvent>) =
        monitorDownEvents.subscribe(observer)

    fun subscribeToRedirectEvents(observer: Observer<RedirectEvent>) =
        redirectEvents.subscribe(observer)
}
