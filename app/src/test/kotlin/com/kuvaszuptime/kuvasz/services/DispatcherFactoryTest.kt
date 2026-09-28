package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
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

    fun dispatchers(useVirtualThreadScheduling: Boolean) =
        ScheduledCheckDispatchers(AppConfig().apply { this.useVirtualThreadScheduling = useVirtualThreadScheduling })

    suspend fun CoroutineDispatcher.currentThread(): Thread = withContext(this) { Thread.currentThread() }

    fun CoroutineDispatcher.executor() =
        shouldBeInstanceOf<ExecutorCoroutineDispatcher>().executor.shouldBeInstanceOf<ExecutorService>()

    given("the ScheduledCheckDispatchers") {

        `when`("the virtual thread scheduling is disabled") {

            then("every check should run on the IO dispatcher, and closing it should be a no-op") {
                val dispatchers = dispatchers(useVirtualThreadScheduling = false)

                dispatchers.default.toString() shouldBe IO_DISPATCHER_NAME
                dispatchers.icmp.toString() shouldBe IO_DISPATCHER_NAME

                dispatchers.close()
                dispatchers.default.currentThread().isVirtual.shouldBeFalse()
            }
        }

        `when`("the virtual thread scheduling is enabled") {

            then("it should run everything on named, separate virtual threads until it is closed") {
                val dispatchers = dispatchers(useVirtualThreadScheduling = true)

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
                val dispatchers = dispatchers(useVirtualThreadScheduling = true)
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

        `when`("the virtual thread scheduling is disabled by default") {

            then("the injected dispatchers should be the IO dispatcher") {
                val ctx = testAppContext()

                ctx.getBean<AppConfig>().useVirtualThreadScheduling.shouldBeFalse()
                ctx.getBean<CoroutineDispatcher>().toString() shouldBe IO_DISPATCHER_NAME
                ctx.getIcmpCheckDispatcher().toString() shouldBe IO_DISPATCHER_NAME
            }
        }

        `when`("the virtual thread scheduling is enabled via the config") {

            then("the injected dispatchers should use virtual threads, and the ICMP checks should get a limited one") {
                val ctx = testAppContext(mapOf("app-config.use-virtual-thread-scheduling" to "TRUE"))
                val dispatchers = ctx.getBean<ScheduledCheckDispatchers>()
                val dispatcher = ctx.getBean<CoroutineDispatcher>()

                ctx.getBean<AppConfig>().useVirtualThreadScheduling.shouldBeTrue()
                dispatcher shouldBeSameInstanceAs dispatchers.default
                dispatcher.currentThread().isVirtual.shouldBeTrue()
                ctx.getIcmpCheckDispatcher() shouldBeSameInstanceAs dispatchers.icmp
                ctx.getIcmpCheckDispatcher() shouldNotBe dispatcher
                ctx.getBean<IcmpCheckScheduler>()

                ctx.close()
                dispatcher.executor().isShutdown.shouldBeTrue()
            }

            then("the dispatcher should be shut down only after the schedulers drained the in-flight checks") {
                val ctx = testAppContext(mapOf("app-config.use-virtual-thread-scheduling" to true))
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

private const val IO_DISPATCHER_NAME = "Dispatchers.IO"
private const val IN_FLIGHT_MONITOR_ID = -1L
