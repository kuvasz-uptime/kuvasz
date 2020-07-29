@file:Suppress("RedundantLambdaArrow")
package com.akobor.kuvasz.util

import org.jooq.Configuration
import org.jooq.DAO
import org.jooq.TableRecord
import org.jooq.TransactionalCallable

fun <R : TableRecord<R>, P, T> DAO<R, P, T>.transaction(block: () -> Unit) {
    configuration().dsl().transaction { _: Configuration -> block() }
}

fun <R : TableRecord<R>, P, T, Result> DAO<R, P, T>.transactionResult(block: () -> Result): Result =
    configuration().dsl().transactionResult(TransactionalCallable {
        block()
    })
