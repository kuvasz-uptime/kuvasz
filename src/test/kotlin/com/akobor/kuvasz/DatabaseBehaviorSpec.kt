package com.akobor.kuvasz

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.flywaydb.core.Flyway
import javax.inject.Inject

abstract class DatabaseBehaviorSpec : BehaviorSpec() {

    @Inject
    lateinit var flyway: Flyway

    override fun afterTest(testCase: TestCase, result: TestResult) {
        flyway.clean()
        flyway.migrate()
    }
}
