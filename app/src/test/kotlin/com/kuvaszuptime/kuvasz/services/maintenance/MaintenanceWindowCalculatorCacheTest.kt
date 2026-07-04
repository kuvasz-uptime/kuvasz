package com.kuvaszuptime.kuvasz.services.maintenance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class MaintenanceWindowCalculatorCacheTest(
    private val calculator: MaintenanceWindowCalculator,
) : StringSpec({

    "a compiled cron expression is cached and reused for the same raw expression" {
        val first = calculator.parseCron("0 2 * * *", "window-a").shouldNotBeNull()
        // A different window name must not affect the lookup: the cache is keyed on the raw expression only
        val second = calculator.parseCron("0 2 * * *", "window-b").shouldNotBeNull()

        first shouldBeSameInstanceAs second
    }
})
