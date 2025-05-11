package com.kuvaszuptime.kuvasz

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import jakarta.inject.Inject
import org.jooq.DSLContext

abstract class DatabaseBehaviorSpec(body: BehaviorSpec.() -> Unit = {}) : BehaviorSpec(body) {

    @Inject
    lateinit var dslContext: DSLContext

    override suspend fun afterContainer(testCase: TestCase, result: TestResult) {
        dslContext.resetDatabase()
    }
}

fun DSLContext.resetDatabase() {
    Kuvasz.KUVASZ.tables.forEach { table ->
        this.deleteFrom(table).execute()
    }
}
