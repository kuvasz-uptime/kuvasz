package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class UptimeCheckLockRegistryTest : BehaviorSpec({

    val lockRegistry = UptimeCheckLockRegistry(
        AppConfig().apply {
            httpCheckLockTimeoutMillis = 3000 // Set a short timeout for testing
        }
    )

    given("the lock acquisition logic") {

        `when`("there is no lock") {

            then("it should acquire the lock") {

                lockRegistry.tryAcquire(1).shouldBeTrue()
            }
        }

        `when`("the lock is already acquired") {

            lockRegistry.tryAcquire(2).shouldBeTrue()

            then("it should not acquire the lock") {

                lockRegistry.tryAcquire(2).shouldBeFalse()
            }
        }

        `when`("the lock is released") {

            lockRegistry.tryAcquire(3).shouldBeTrue()
            lockRegistry.release(3)

            then("it should acquire the lock again") {

                lockRegistry.tryAcquire(3).shouldBeTrue()
            }
        }

        `when`("the lock is acquired and timeout is reached") {

            lockRegistry.tryAcquire(4).shouldBeTrue()

            delay(1000.milliseconds) // Sleep for 1 second to simulate some processing time

            then("it should not allow acquiring the lock again") {

                lockRegistry.tryAcquire(4).shouldBeFalse()
            }

            // Simulate the passage of time beyond the lock timeout
            delay(2001.milliseconds) // Sleep for slightly more than the lock timeout

            then("it should allow acquiring the lock again") {

                lockRegistry.tryAcquire(4).shouldBeTrue()
            }
        }
    }

    given("the draining logic") {

        fun newRegistry() = UptimeCheckLockRegistry(AppConfig())

        `when`("there are running checks") {

            then("it should wait for them to finish, and it should not let any new check start") {
                val registry = newRegistry()
                registry.tryAcquire(1).shouldBeTrue()
                Thread.ofVirtual().start {
                    Thread.sleep(300)
                    registry.release(1)
                }

                measureTime { registry.drain(Duration.ofSeconds(5)) } shouldBeLessThan 5.seconds

                registry.tryAcquire(1).shouldBeFalse()
                registry.tryAcquire(2).shouldBeFalse()
            }
        }

        `when`("a running check does not finish within the grace period") {

            then("it should stop waiting, and the subsequent calls should not restart the grace period") {
                val registry = newRegistry()
                registry.tryAcquire(1).shouldBeTrue()

                measureTime { registry.drain(Duration.ofMillis(500)) } shouldBeGreaterThanOrEqualTo 500.milliseconds
                measureTime { registry.drain(Duration.ofSeconds(5)) } shouldBeLessThan 1.seconds
            }
        }

        `when`("there are no running checks") {

            then("it should return immediately") {
                measureTime { newRegistry().drain(Duration.ofSeconds(5)) } shouldBeLessThan 1.seconds
            }
        }
    }
})
