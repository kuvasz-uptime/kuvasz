package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DispatcherFactoryTest : BehaviorSpec({

    suspend fun CoroutineDispatcher.currentThread(): Thread = withContext(this) { Thread.currentThread() }

    fun CoroutineDispatcher.executor() =
        shouldBeInstanceOf<ExecutorCoroutineDispatcher>().executor.shouldBeInstanceOf<ExecutorService>()

    given("the ScheduledCheckDispatchers") {

        `when`("it is created") {

            then("it should run everything on named, separate virtual threads until it is closed") {
                val dispatchers = ScheduledCheckDispatchers()

                val firstThread = dispatchers.default.currentThread()
                val secondThread = dispatchers.default.currentThread()

                firstThread.isVirtual.shouldBeTrue()
                firstThread.name shouldStartWith ScheduledCheckDispatchers.VIRTUAL_THREAD_NAME_PREFIX
                (firstThread.threadId() == secondThread.threadId()).shouldBeFalse()
                dispatchers.icmp.currentThread().isVirtual.shouldBeTrue()
                dispatchers.default.executor().isShutdown.shouldBeFalse()

                dispatchers.close()
                dispatchers.default.executor().isShutdown.shouldBeTrue()
            }

            then("only a limited number of ICMP checks should run at the same time") {
                val dispatchers = ScheduledCheckDispatchers()
                val maxChecks = ScheduledCheckDispatchers.MAX_CONCURRENT_ICMP_CHECKS
                val running = AtomicInteger()
                val maxRunning = AtomicInteger()
                val executed = AtomicInteger()
                val release = CountDownLatch(1)
                val scope = CoroutineScope(dispatchers.icmp)

                repeat(maxChecks + 1) {
                    scope.launch {
                        // Blocking, just like a ping
                        maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                        release.await()
                        running.decrementAndGet()
                        executed.incrementAndGet()
                    }
                }
                eventually(10.seconds) { running.get() shouldBe maxChecks }
                // Giving the check over the limit a chance to (wrongly) start too
                delay(500.milliseconds)
                running.get() shouldBe maxChecks
                release.countDown()

                eventually(10.seconds) { executed.get() shouldBe maxChecks + 1 }
                maxRunning.get() shouldBe maxChecks
                dispatchers.close()
            }
        }
    }

    given("the application context") {

        `when`("the context is started") {

            then("the injected dispatchers should use virtual threads, and the ICMP checks should get a limited one") {
                val ctx = testAppContext()
                val dispatchers = ctx.getBean<ScheduledCheckDispatchers>()
                val dispatcher = ctx.getBean<CoroutineDispatcher>()

                dispatcher shouldBeSameInstanceAs dispatchers.default
                dispatcher.currentThread().isVirtual.shouldBeTrue()
                ctx.getIcmpCheckDispatcher() shouldBeSameInstanceAs dispatchers.icmp
                ctx.getIcmpCheckDispatcher() shouldNotBe dispatcher
                ctx.getBean<IcmpCheckScheduler>()

                ctx.close()
                dispatcher.executor().isShutdown.shouldBeTrue()
            }

            then("the dispatcher should be shut down only after the schedulers drained the in-flight checks") {
                val ctx = testAppContext()
                val lockRegistry = ctx.getBean<UptimeCheckLockRegistry>()
                // A scheduler has to exist, it's the one draining the registry on shutdown
                ctx.getBean<HttpCheckScheduler>()
                val checkStarted = CountDownLatch(1)
                val checkFinished = AtomicBoolean(false)
                CoroutineScope(ctx.getBean<CoroutineDispatcher>()).launch {
                    lockRegistry.tryAcquire(IN_FLIGHT_MONITOR_ID).shouldBeTrue()
                    checkStarted.countDown()
                    try {
                        // Suspending, so the check has to be resumed on the dispatcher while it's being drained
                        delay(500.milliseconds)
                        checkFinished.set(true)
                    } finally {
                        lockRegistry.release(IN_FLIGHT_MONITOR_ID)
                    }
                }
                checkStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()

                ctx.close()

                checkFinished.get().shouldBeTrue()
            }
        }
    }
})

private fun ApplicationContext.getIcmpCheckDispatcher(): CoroutineDispatcher =
    getBean(CoroutineDispatcher::class.java, Qualifiers.byName(DispatcherFactory.ICMP_CHECK_DISPATCHER))

private const val IN_FLIGHT_MONITOR_ID = -1L
