package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import jakarta.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class EventDispatcher {

    private val httpUpEvents = serializedSubject<HttpMonitorUpEvent>()
    private val httpDownEvents = serializedSubject<HttpMonitorDownEvent>()
    private val pushUptimeEvents = serializedSubject<PushUptimeMonitorEvent>()
    private val icmpUpEvents = serializedSubject<IcmpMonitorUpEvent>()
    private val icmpDownEvents = serializedSubject<IcmpMonitorDownEvent>()
    private val httpRedirectEvents = serializedSubject<HttpRedirectEvent>()
    private val sslValidEvents = serializedSubject<SSLValidEvent>()
    private val sslWillExpireEvents = serializedSubject<SSLWillExpireEvent>()
    private val sslInvalidEvents = serializedSubject<SSLInvalidEvent>()
    private val monitorLifecycleEvents = serializedSubject<MonitorLifecycleEvent>()
    private val maintenanceStartEvents = serializedSubject<MaintenanceWindowStartEvent>()
    private val maintenanceEndEvents = serializedSubject<MaintenanceWindowEndEvent>()

    fun dispatch(event: MonitorEvent<*>) =
        when (event) {
            is HttpMonitorUpEvent -> httpUpEvents.onNext(event)
            is HttpMonitorDownEvent -> httpDownEvents.onNext(event)
            is HttpRedirectEvent -> httpRedirectEvents.onNext(event)
            is SSLValidEvent -> sslValidEvents.onNext(event)
            is SSLInvalidEvent -> sslInvalidEvents.onNext(event)
            is SSLWillExpireEvent -> sslWillExpireEvents.onNext(event)
            is PushMonitorDownEvent, is PushMonitorUpEvent -> pushUptimeEvents.onNext(event)
            is IcmpMonitorUpEvent -> icmpUpEvents.onNext(event)
            is IcmpMonitorDownEvent -> icmpDownEvents.onNext(event)
        }

    fun dispatch(event: MonitorLifecycleEvent) {
        monitorLifecycleEvents.onNext(event)
    }

    fun dispatch(event: MaintenanceWindowEvent) =
        when (event) {
            is MaintenanceWindowStartEvent -> maintenanceStartEvents.onNext(event)
            is MaintenanceWindowEndEvent -> maintenanceEndEvents.onNext(event)
        }

    private inline fun <reified T : Any> Subject<T>.safeSubscribeOnIo(
        crossinline consumer: (T) -> Unit
    ): Disposable =
        subscribeOn(Schedulers.io())
            .subscribe { event ->
                runCatching { consumer(event) }
                    .exceptionOrNull()
                    ?.let { logger.error("Error while processing a ${T::class.simpleName}", it) }
            }

    fun subscribeToHttpMonitorUpEvents(consumer: (HttpMonitorUpEvent) -> Unit): Disposable =
        httpUpEvents.safeSubscribeOnIo(consumer)

    fun subscribeToHttpMonitorDownEvents(consumer: (HttpMonitorDownEvent) -> Unit): Disposable =
        httpDownEvents.safeSubscribeOnIo(consumer)

    fun subscribeToPushMonitorEvents(consumer: (PushUptimeMonitorEvent) -> Unit): Disposable =
        pushUptimeEvents.safeSubscribeOnIo(consumer)

    fun subscribeToIcmpMonitorUpEvents(consumer: (IcmpMonitorUpEvent) -> Unit): Disposable =
        icmpUpEvents.safeSubscribeOnIo(consumer)

    fun subscribeToIcmpMonitorDownEvents(consumer: (IcmpMonitorDownEvent) -> Unit): Disposable =
        icmpDownEvents.safeSubscribeOnIo(consumer)

    fun subscribeToHttpRedirectEvents(consumer: (HttpRedirectEvent) -> Unit): Disposable =
        httpRedirectEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLValidEvents(consumer: (SSLValidEvent) -> Unit): Disposable =
        sslValidEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLInvalidEvents(consumer: (SSLInvalidEvent) -> Unit): Disposable =
        sslInvalidEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLWillExpireEvents(consumer: (SSLWillExpireEvent) -> Unit): Disposable =
        sslWillExpireEvents.safeSubscribeOnIo(consumer)

    fun subscribeToMonitorLifecycleEvents(consumer: (MonitorLifecycleEvent) -> Unit): Disposable =
        monitorLifecycleEvents.safeSubscribeOnIo(consumer)

    fun subscribeToMaintenanceStartEvents(consumer: (MaintenanceWindowStartEvent) -> Unit): Disposable =
        maintenanceStartEvents.safeSubscribeOnIo(consumer)

    fun subscribeToMaintenanceEndEvents(consumer: (MaintenanceWindowEndEvent) -> Unit): Disposable =
        maintenanceEndEvents.safeSubscribeOnIo(consumer)

    companion object {
        private val logger = loggerFor<EventDispatcher>()
    }
}

private fun <T : Any> serializedSubject(): Subject<T> = PublishSubject.create<T>().toSerialized()
