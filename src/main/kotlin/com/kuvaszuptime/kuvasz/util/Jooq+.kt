@file:Suppress("RedundantLambdaArrow")

package com.kuvaszuptime.kuvasz.util

import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import org.jooq.InsertResultStep
import org.jooq.TableRecord
import org.jooq.UpdateResultStep
import org.jooq.exception.DataAccessException
import org.jooq.exception.NoDataFoundException
import org.postgresql.util.PSQLException

fun DataAccessException.toPersistenceError(): PersistenceError =
    getCause(PSQLException::class.java)?.message?.let { message ->
        if (message.contains("duplicate key")) DuplicationError() else PersistenceError(message)
    } ?: PersistenceError(message)

inline fun <R : TableRecord<R>, reified O> InsertResultStep<R>.fetchOneIntoOrThrow(): O =
    fetchOne()?.into(O::class.java) ?: throw NoDataFoundException()

inline fun <R : TableRecord<R>, reified O> UpdateResultStep<R>.fetchOneIntoOrThrow(): O =
    fetchOne()?.into(O::class.java) ?: throw NoDataFoundException()
