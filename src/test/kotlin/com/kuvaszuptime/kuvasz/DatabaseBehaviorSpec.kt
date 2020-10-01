package com.kuvaszuptime.kuvasz

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.flywaydb.core.Flyway
import javax.inject.Inject

abstract class DatabaseBehaviorSpec(body: BehaviorSpec.() -> Unit = {}) : BehaviorSpec(body) {

    @Inject
    lateinit var flyway: Flyway

    override fun afterContainer(testCase: TestCase, result: TestResult) {
        flyway.clean()
        flyway.migrate()
    }
}
