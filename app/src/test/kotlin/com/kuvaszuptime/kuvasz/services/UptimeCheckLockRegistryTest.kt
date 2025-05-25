package com.kuvaszuptime.kuvasz.services

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class UptimeCheckLockRegistryTest : BehaviorSpec({

    val lockRegistry = UptimeCheckLockRegistry()

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
    }
})
