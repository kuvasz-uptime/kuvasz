package com.akobor.kuvasz

import com.akobor.kuvasz.utils.resetDatabase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.micronaut.runtime.server.EmbeddedServer

open class DatabaseBehaviorSpec : BehaviorSpec(){
    lateinit var service: EmbeddedServer

    override fun afterTest(testCase: TestCase, result: TestResult) {
        service.resetDatabase()
    }
}
