package com.kuvaszuptime.kuvasz.services

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Provides the dispatchers of the scheduled checks. Every check runs on its own virtual thread, so the checks that are
 * blocked until their timeout (e.g. unreachable TCP/ICMP/DNS targets) can't delay the rest.
 */
@Singleton
class ScheduledCheckDispatchers : AutoCloseable {

    private val virtualThreadExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(VIRTUAL_THREAD_NAME_PREFIX, 0).factory())

    val default: CoroutineDispatcher = virtualThreadExecutor.asCoroutineDispatcher()

    /**
     * Every ping is a separate process (and needs platform threads), so their number has to be bounded explicitly, to
     * keep a large outage from exhausting the memory. Limiting the dispatcher (instead of the pings themselves) keeps
     * the waiting checks from holding their locks.
     */
    val icmp: CoroutineDispatcher = default.limitedParallelism(MAX_CONCURRENT_ICMP_CHECKS)

    /**
     * Not awaiting the termination, the schedulers take care of draining their in-flight checks. It's not done by the
     * factory, because the schedulers only depend (transitively) on this bean, so it's guaranteed to be destroyed
     * after them.
     */
    @PreDestroy
    override fun close() {
        virtualThreadExecutor.shutdown()
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
