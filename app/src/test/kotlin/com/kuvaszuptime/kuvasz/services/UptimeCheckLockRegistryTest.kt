package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.AppConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.delay

class UptimeCheckLockRegistryTest : BehaviorSpec({

    val lockRegistry = UptimeCheckLockRegistry(
        AppConfig().apply {
            uptimeCheckLockTimeoutMs = 3000 // Set a short timeout for testing
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

            delay(1000) // Sleep for 1 second to simulate some processing time

            then("it should not allow acquiring the lock again") {

                lockRegistry.tryAcquire(4).shouldBeFalse()
            }

            // Simulate the passage of time beyond the lock timeout
            delay(2001) // Sleep for slightly more than the lock timeout

            then("it should allow acquiring the lock again") {

                lockRegistry.tryAcquire(4).shouldBeTrue()
            }
        }
    }
})
