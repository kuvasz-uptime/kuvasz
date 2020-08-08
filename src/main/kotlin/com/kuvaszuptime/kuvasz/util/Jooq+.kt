@file:Suppress("RedundantLambdaArrow")

package com.kuvaszuptime.kuvasz.util

import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import org.jooq.Configuration
import org.jooq.DAO
import org.jooq.TableRecord
import org.jooq.TransactionalCallable
import org.jooq.exception.DataAccessException
import org.postgresql.util.PSQLException

fun <R : TableRecord<R>, P, T> DAO<R, P, T>.transaction(block: () -> Unit) {
    configuration().dsl().transaction { _: Configuration -> block() }
}

fun <R : TableRecord<R>, P, T, Result> DAO<R, P, T>.transactionResult(block: () -> Result): Result =
    configuration().dsl().transactionResult(TransactionalCallable {
        block()
    })

fun DataAccessException.toPersistenceError(): PersistenceError {
    val causeMessage = this.getCause(PSQLException::class.java)?.message
    return if (causeMessage != null) {
        when {
            causeMessage.contains("duplicate key") -> DuplicationError()
            else -> PersistenceError(causeMessage)
        }
    } else PersistenceError(message)
}
