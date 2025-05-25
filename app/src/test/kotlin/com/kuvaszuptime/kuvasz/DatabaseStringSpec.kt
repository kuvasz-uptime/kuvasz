package com.kuvaszuptime.kuvasz

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import jakarta.inject.Inject
import org.jooq.DSLContext

abstract class DatabaseStringSpec(body: StringSpec.() -> Unit = {}) : StringSpec(body) {

    @Inject
    lateinit var dslContext: DSLContext

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        dslContext.resetDatabase()
    }
}
