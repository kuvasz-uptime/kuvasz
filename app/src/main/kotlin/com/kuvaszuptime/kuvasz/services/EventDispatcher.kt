package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@Suppress("TooManyFunctions")
class EventDispatcher {

    private val httpUpEvents = PublishSubject.create<HttpMonitorUpEvent>().toSerialized()
    private val httpDownEvents = PublishSubject.create<HttpMonitorDownEvent>().toSerialized()
    private val pushUptimeEvents = PublishSubject.create<PushUptimeMonitorEvent>().toSerialized()
    private val httpRedirectEvents = PublishSubject.create<HttpRedirectEvent>().toSerialized()
    private val sslValidEvents = PublishSubject.create<SSLValidEvent>().toSerialized()
    private val sslWillExpireEvents = PublishSubject.create<SSLWillExpireEvent>().toSerialized()
    private val sslInvalidEvents = PublishSubject.create<SSLInvalidEvent>().toSerialized()
    private val monitorLifecycleEvents = PublishSubject.create<MonitorLifecycleEvent>().toSerialized()

    fun dispatch(event: MonitorEvent<*>) =
        when (event) {
            is HttpMonitorUpEvent -> httpUpEvents.onNext(event)
            is HttpMonitorDownEvent -> httpDownEvents.onNext(event)
            is HttpRedirectEvent -> httpRedirectEvents.onNext(event)
            is SSLValidEvent -> sslValidEvents.onNext(event)
            is SSLInvalidEvent -> sslInvalidEvents.onNext(event)
            is SSLWillExpireEvent -> sslWillExpireEvents.onNext(event)
            is PushMonitorDownEvent, is PushMonitorUpEvent -> pushUptimeEvents.onNext(event)
        }

    fun dispatch(event: MonitorLifecycleEvent) {
        monitorLifecycleEvents.onNext(event)
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

    companion object {
        private val logger = LoggerFactory.getLogger(EventDispatcher::class.java)
    }
}
