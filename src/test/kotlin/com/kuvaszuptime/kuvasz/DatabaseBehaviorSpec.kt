package com.kuvaszuptime.kuvasz

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import jakarta.inject.Inject
import org.flywaydb.core.Flyway

abstract class DatabaseBehaviorSpec(body: BehaviorSpec.() -> Unit = {}) : BehaviorSpec(body) {

    @Inject
    lateinit var flyway: Flyway

    override fun afterContainer(testCase: TestCase, result: TestResult) {
        flyway.clean()
        flyway.migrate()
    }
}
