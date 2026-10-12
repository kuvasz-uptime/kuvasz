package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The push endpoints are called concurrently by the clients of the push monitors, so a request must never need more
 * than one connection at a time: otherwise the requests, each holding one connection and waiting for another, can
 * exhaust the pool for good (the tests have a pool of 2 connections only).
 */
@MicronautTest(startApplication = false)
// A starving request fails fast, instead of after the default 30 seconds
@Property(name = "datasources.default.connection-timeout", value = "3000")
class PushMonitorConcurrencyTest(
    private val pushMonitorActions: PushMonitorActions,
    private val pushMonitorRepository: PushMonitorRepository,
    private val uptimeEventRepository: PushUptimeEventRepository,
) : DatabaseBehaviorSpec() {

    /** Runs the given action for every client secret at the very same time, returning their results */
    private fun <T> runConcurrently(secrets: List<String>, action: (String) -> T): List<T> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(secrets.size)
        val results = secrets.map { secret ->
            executor.submit(
                Callable {
                    start.await()
                    action(secret)
                }
            )
        }
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

        return results.map { result -> result.get() }
    }

    init {
        given("many push monitors that report at the same time") {

            `when`("all of them signal a failure, and then send a heartbeat concurrently") {
                val monitors = (1..CONCURRENT_CLIENTS).map { createPushMonitor(pushMonitorRepository) }
                val secrets = monitors.map { monitor -> monitor.clientSecret }

                val failed = runConcurrently(secrets) { secret -> pushMonitorActions.signalFailure(secret, "Boom") }
                val downEvents = monitors.map { monitor -> uptimeEventRepository.fetchByMonitorId(monitor.id) }
                val recovered = runConcurrently(secrets) { secret ->
                    pushMonitorActions.updateLastHeartbeat(secret, getCurrentTimestamp())
                }

                then("every request should succeed, without exhausting the connection pool") {
                    failed.forAll { monitor -> monitor.shouldNotBeNull() }
                    downEvents.forAll { events -> events.single().status shouldBe UptimeStatus.DOWN }

                    recovered.forAll { monitor -> monitor.shouldNotBeNull() }
                    monitors.forAll { monitor ->
                        val events = uptimeEventRepository.fetchByMonitorId(monitor.id)
                        events.sortedBy { event -> event.startedAt }.map { event -> event.status } shouldBe
                            listOf(UptimeStatus.DOWN, UptimeStatus.UP)
                    }
                }
            }
        }
    }

    companion object {
        private const val CONCURRENT_CLIENTS = 10
        private const val TIMEOUT_SECONDS = 30L
    }
}
