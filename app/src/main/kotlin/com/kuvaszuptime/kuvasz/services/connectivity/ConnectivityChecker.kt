package com.kuvaszuptime.kuvasz.services.connectivity

import com.kuvaszuptime.kuvasz.config.ConnectivityCheckConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import com.kuvaszuptime.kuvasz.services.check.gracefulCancel
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpConnectExecutor
import com.kuvaszuptime.kuvasz.util.durationBetween
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference

@Singleton
@Requires(bean = ConnectivityCheckConfig::class)
class ConnectivityChecker(
    private val config: ConnectivityCheckConfig,
    private val connectExecutor: TcpConnectExecutor,
    private val dispatcher: CoroutineDispatcher,
) {

    private val snapshot = AtomicReference(ConnectivitySnapshot())

    fun isOutboundConnectivityLost(): Boolean = snapshot.get().state == ConnectivityState.DOWN

    fun isCheckSuppressedFor(monitor: MonitorRecord): Boolean =
        isOutboundConnectivityLost() && !monitor.ignoreConnectivityCheck

    fun getStatus(): ConnectivityStatus = snapshot.get().let { current ->
        ConnectivityStatus(
            state = current.state,
            targets = config.targets,
            intervalSeconds = config.intervalSeconds,
            timeoutSeconds = config.timeoutSeconds,
            lastCheckedAt = current.lastCheckedAt,
            lastSuccessfulCheckAt = current.lastSuccessfulCheckAt,
            downSince = current.downSince,
            lastError = current.lastError,
        )
    }

    /**
     * Runs a probe and updates the state accordingly. A failing round is re-tried right away instead of waiting for
     * the next interval, and the state is only allowed to flip to DOWN if every attempt fails - a single dropped
     * packet shouldn't be enough to suspend the checks of every monitor.
     *
     * The other direction is deliberately asymmetric: one successful round is enough to consider the connectivity
     * restored, so recovery is never delayed.
     *
     * @param confirmFailures whether a failing round should be confirmed by further attempts
     */
    suspend fun check(confirmFailures: Boolean = true) {
        val attempts = if (confirmFailures) FAILURE_CONFIRMATION_ATTEMPTS else 1
        var result = ProbeResult(connected = false, failure = null)

        for (attempt in 1..attempts) {
            if (attempt > 1) {
                logger.debug("The connectivity probe failed, re-trying it ($attempt/$attempts)")
                delay(CONFIRMATION_DELAY_MILLIS)
            }
            result = probeOnce()
            if (result.connected) break
        }

        transitionTo(result)
    }

    /**
     * Dials the configured targets one by one, and stops at the first one that accepts the connection. The dialing
     * itself is blocking, so it's offloaded instead of occupying the caller's thread.
     */
    private suspend fun probeOnce(): ProbeResult = withContext(dispatcher) {
        var lastFailure: String? = null
        val connected = config.parsedTargets.any { target ->
            val result = connectExecutor.execute(target.host, target.port, config.timeoutMillis)
            if (!result.isConnected) {
                lastFailure = "$target (${result.error})"
            }
            result.isConnected
        }

        ProbeResult(connected, lastFailure)
    }

    private fun transitionTo(result: ProbeResult) {
        val now = getCurrentTimestamp()

        val previous = snapshot.getAndUpdate { current ->
            if (result.connected) {
                current.copy(
                    state = ConnectivityState.UP,
                    lastCheckedAt = now,
                    lastSuccessfulCheckAt = now,
                    downSince = null,
                    lastError = null,
                )
            } else {
                current.copy(
                    state = ConnectivityState.DOWN,
                    lastCheckedAt = now,
                    downSince = current.downSince ?: now,
                    lastError = result.failure,
                )
            }
        }

        logTransition(previous, result, now)
    }

    /**
     * Only the transitions deserve a log entry above debug level: an ongoing outage shouldn't report itself over
     * and over again on every single probe.
     */
    private fun logTransition(previous: ConnectivitySnapshot, result: ProbeResult, now: OffsetDateTime) {
        val wasDown = previous.state == ConnectivityState.DOWN

        when {
            result.connected && wasDown -> logger.info(
                "Kuvasz has regained its outbound network connectivity" +
                    previous.downSince?.durationBetween(now)?.let { " after $it" }.orEmpty() +
                    ". The suspended checks are resumed."
            )

            result.connected -> logger.debug("Kuvasz has outbound network connectivity")

            wasDown -> logger.debug("Kuvasz still has no outbound network connectivity")

            else -> logger.warn(
                "Kuvasz has lost its outbound network connectivity: none of the configured targets " +
                    "(${config.targets.joinToString()}) could be reached" +
                    result.failure?.let { ", last error: $it" }.orEmpty() +
                    ". Every check Kuvasz initiates on its own is suspended until the connectivity is restored."
            )
        }
    }

    private data class ProbeResult(val connected: Boolean, val failure: String?)

    private data class ConnectivitySnapshot(
        val state: ConnectivityState = ConnectivityState.UNKNOWN,
        val lastCheckedAt: OffsetDateTime? = null,
        val lastSuccessfulCheckAt: OffsetDateTime? = null,
        val downSince: OffsetDateTime? = null,
        val lastError: String? = null,
    )

    companion object {
        private val logger = loggerFor<ConnectivityChecker>()

        internal const val FAILURE_CONFIRMATION_ATTEMPTS = 3
        internal const val CONFIRMATION_DELAY_MILLIS = 1000L
    }
}

@Singleton
@Requires(bean = ConnectivityCheckConfig::class)
class ConnectivityCheckScheduler(
    @param:Named(TaskExecutors.SCHEDULED) private val taskScheduler: TaskScheduler,
    private val config: ConnectivityCheckConfig,
    private val connectivityChecker: ConnectivityChecker,
    dispatcher: CoroutineDispatcher,
) : AutoCloseable {

    private val coroutineExHandler = CoroutineExceptionHandler { _, ex ->
        logger.warn("Coroutine failed with ${ex::class.simpleName}: ${ex.message}")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + coroutineExHandler)

    private val probeLock = Mutex()

    private var scheduledCheck: ScheduledFuture<*>? = null

    fun initialize() {
        // Priming the state before the first check of any monitor is scheduled, so an instance that starts up
        // during an outage doesn't emit a burst of false events. The failure confirmation is skipped here on
        // purpose: it would make the startup considerably slower, and an unconfirmed failure corrects itself at
        // the next probe anyway.
        runBlocking { runProbe(confirmFailures = false) }

        val period = Duration.ofSeconds(config.intervalSeconds)
        scheduledCheck = taskScheduler.scheduleWithFixedDelay(period, period) {
            scope.launch { runProbe() }
        }
        logger.info(
            "The connectivity check has been set up successfully, targets: ${config.targets.joinToString()}"
        )
    }

    /**
     * An escaping exception would make the executor cancel the repeating task silently, stopping the connectivity
     * checks until the next restart, hence the catch-all here.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runProbe(confirmFailures: Boolean = true) {
        if (!probeLock.tryLock()) {
            logger.debug("Skipping the connectivity probe, because the previous one is still in progress")
            return
        }
        try {
            connectivityChecker.check(confirmFailures)
        } catch (ex: Exception) {
            logger.error("An unexpected error happened during the connectivity check: ${ex.message}", ex)
        } finally {
            probeLock.unlock()
        }
    }

    @PreDestroy
    override fun close() {
        scheduledCheck.gracefulCancel()
        scope.cancel()
    }

    companion object {
        private val logger = loggerFor<ConnectivityCheckScheduler>()
    }
}
