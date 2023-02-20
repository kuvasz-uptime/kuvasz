package com.kuvaszuptime.kuvasz

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import jakarta.inject.Inject
import org.jooq.Configuration

abstract class DatabaseBehaviorSpec(body: BehaviorSpec.() -> Unit = {}) : BehaviorSpec(body) {

    @Inject
    lateinit var jooqConfig: Configuration

    override suspend fun afterContainer(testCase: TestCase, result: TestResult) {
        jooqConfig.dsl().query(
            """
            DO ${'$'}${'$'} DECLARE
                r RECORD;
            BEGIN
                FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'kuvasz' 
                            AND tablename <> 'flyway_schema_history') LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                END LOOP;
            END ${'$'}${'$'};
            """.trimIndent()
        ).execute()
    }
}
