package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
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
class EventDispatcher {

    private val monitorUpEvents = PublishSubject.create<MonitorUpEvent>().toSerialized()
    private val monitorDownEvents = PublishSubject.create<MonitorDownEvent>().toSerialized()
    private val redirectEvents = PublishSubject.create<RedirectEvent>().toSerialized()
    private val sslValidEvents = PublishSubject.create<SSLValidEvent>().toSerialized()
    private val sslWillExpireEvents = PublishSubject.create<SSLWillExpireEvent>().toSerialized()
    private val sslInvalidEvents = PublishSubject.create<SSLInvalidEvent>().toSerialized()

    fun dispatch(event: MonitorEvent) =
        when (event) {
            is MonitorUpEvent -> monitorUpEvents.onNext(event)
            is MonitorDownEvent -> monitorDownEvents.onNext(event)
            is RedirectEvent -> redirectEvents.onNext(event)
            is SSLValidEvent -> sslValidEvents.onNext(event)
            is SSLInvalidEvent -> sslInvalidEvents.onNext(event)
            is SSLWillExpireEvent -> sslWillExpireEvents.onNext(event)
        }

    private inline fun <reified T : MonitorEvent> Subject<T>.safeSubscribeOnIo(
        crossinline consumer: (T) -> Unit
    ): Disposable =
        subscribeOn(Schedulers.io())
            .subscribe { event ->
                runCatching { consumer(event) }
                    .exceptionOrNull()
                    ?.let { logger.error("Error while processing a ${T::class.simpleName}", it) }
            }

    fun subscribeToMonitorUpEvents(consumer: (MonitorUpEvent) -> Unit): Disposable =
        monitorUpEvents.safeSubscribeOnIo(consumer)

    fun subscribeToMonitorDownEvents(consumer: (MonitorDownEvent) -> Unit): Disposable =
        monitorDownEvents.safeSubscribeOnIo(consumer)

    fun subscribeToRedirectEvents(consumer: (RedirectEvent) -> Unit): Disposable =
        redirectEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLValidEvents(consumer: (SSLValidEvent) -> Unit): Disposable =
        sslValidEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLInvalidEvents(consumer: (SSLInvalidEvent) -> Unit): Disposable =
        sslInvalidEvents.safeSubscribeOnIo(consumer)

    fun subscribeToSSLWillExpireEvents(consumer: (SSLWillExpireEvent) -> Unit): Disposable =
        sslWillExpireEvents.safeSubscribeOnIo(consumer)

    companion object {
        private val logger = LoggerFactory.getLogger(EventDispatcher::class.java)
    }
}
