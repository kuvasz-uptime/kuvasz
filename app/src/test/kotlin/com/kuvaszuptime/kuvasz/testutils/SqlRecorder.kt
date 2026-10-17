package com.kuvaszuptime.kuvasz.testutils

import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.impl.DefaultExecuteListenerProvider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records the SQL of every statement executed through [dsl], which shares the configuration (and so the database)
 * of the given context. Meant for the tests that check what a query reads, not only what it returns.
 */
class SqlRecorder(dslContext: DSLContext) {

    private val recorded = CopyOnWriteArrayList<String>()

    val statements: List<String> = recorded

    val dsl: DSLContext = dslContext.configuration()
        .derive(
            DefaultExecuteListenerProvider(
                object : ExecuteListener {
                    override fun executeStart(ctx: ExecuteContext) {
                        ctx.sql()?.let(recorded::add)
                    }
                }
            )
        )
        .dsl()

    val countStatements: List<String>
        get() = statements.filter { it.contains("count(*)", ignoreCase = true) }
}
