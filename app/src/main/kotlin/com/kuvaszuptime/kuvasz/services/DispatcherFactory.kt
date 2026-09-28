package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Provides the dispatchers of the scheduled checks. By default, it's the shared IO dispatcher, while the experimental
 * virtual thread mode runs every check on its own virtual thread, so the checks that are blocked until their timeout
 * (e.g. unreachable TCP/ICMP/DNS targets) can't delay the rest.
 */
@Singleton
class ScheduledCheckDispatchers(appConfig: AppConfig) : AutoCloseable {

    private val virtualThreadExecutor = if (appConfig.useVirtualThreadScheduling) {
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(VIRTUAL_THREAD_NAME_PREFIX, 0).factory())
    } else {
        null
    }

    @Suppress("InjectDispatcher")
    val default: CoroutineDispatcher = virtualThreadExecutor?.asCoroutineDispatcher() ?: Dispatchers.IO

    /**
     * Every ping is a separate process (and needs platform threads), so while the IO dispatcher bounds their number
     * implicitly, it has to be done explicitly with virtual threads, to keep a large outage from exhausting the memory.
     * Limiting the dispatcher (instead of the pings themselves) keeps the waiting checks from holding their locks.
     */
    val icmp: CoroutineDispatcher =
        if (virtualThreadExecutor != null) default.limitedParallelism(MAX_CONCURRENT_ICMP_CHECKS) else default

    /**
     * Not awaiting the termination, the schedulers take care of draining their in-flight checks. It's not done by the
     * factory, because the schedulers only depend (transitively) on this bean, so it's guaranteed to be destroyed
     * after them.
     */
    @PreDestroy
    override fun close() {
        virtualThreadExecutor?.shutdown()
    }

    companion object {
        const val VIRTUAL_THREAD_NAME_PREFIX = "scheduled-check-"
        const val MAX_CONCURRENT_ICMP_CHECKS = 64
    }
}

@Factory
class DispatcherFactory {

    @Singleton
    @Primary
    fun provideDispatcher(dispatchers: ScheduledCheckDispatchers): CoroutineDispatcher = dispatchers.default

    @Singleton
    @Named(ICMP_CHECK_DISPATCHER)
    fun provideIcmpCheckDispatcher(dispatchers: ScheduledCheckDispatchers): CoroutineDispatcher = dispatchers.icmp

    companion object {
        const val ICMP_CHECK_DISPATCHER = "icmp-check"
    }
}
