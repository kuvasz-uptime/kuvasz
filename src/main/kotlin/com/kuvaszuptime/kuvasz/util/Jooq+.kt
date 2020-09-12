@file:Suppress("RedundantLambdaArrow")

package com.kuvaszuptime.kuvasz.util

import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import org.jooq.Configuration
import org.jooq.DAO
import org.jooq.TableRecord
import org.jooq.exception.DataAccessException
import org.postgresql.util.PSQLException

fun <R : TableRecord<R>, P, T> DAO<R, P, T>.transaction(block: () -> Unit) {
    configuration().dsl().transaction { _: Configuration -> block() }
}

fun DataAccessException.toPersistenceError(): PersistenceError =
    getCause(PSQLException::class.java)?.message?.let { message ->
        if (message.contains("duplicate key")) DuplicationError() else PersistenceError(message)
    } ?: PersistenceError(message)
