@file:Suppress("RedundantLambdaArrow")

package com.kuvaszuptime.kuvasz.util

import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.InsertResultStep
import org.jooq.Record
import org.jooq.Record1
import org.jooq.Select
import org.jooq.SelectOrderByStep
import org.jooq.SortField
import org.jooq.TableRecord
import org.jooq.UpdateResultStep
import org.jooq.exception.DataAccessException
import org.jooq.exception.NoDataFoundException
import org.jooq.impl.DSL
import org.postgresql.util.PSQLException

fun DataAccessException.toPersistenceException(): PersistenceException =
    getCause(PSQLException::class.java)?.message?.let { message ->
        if (message.contains("duplicate key")) DuplicationException() else PersistenceException(message)
    } ?: PersistenceException(message)

fun Field<String>.ascIgnoreCase(): SortField<String> = DSL.lower(this).asc()

/**
 * Sorts by the field in the given order, without regard to the case of the letters if it's a text. The rows without a
 * value come last in both orders, so the first pages show the ones that have something to sort by.
 */
fun Field<*>.sortedIn(order: SortOrder): SortField<*> {
    val sortable = if (type == String::class.java) DSL.lower(coerce(String::class.java)) else this
    val sorted = when (order) {
        SortOrder.ASC -> sortable.asc()
        SortOrder.DESC -> sortable.desc()
    }
    return sorted.nullsLast()
}

/**
 * Fetches one page of the rows the query selects, along with the number of all of them. The last field of [orderBy]
 * has to be unique (e.g. the ID), otherwise the rows with the same values could show up on more than one page, or on
 * none of them.
 *
 * [countQuery] has to count the very same rows, without selecting or joining anything else, but it's only run when the
 * page can't tell the number itself: a page that has rows, but fewer than it could hold, is the last one.
 */
fun <T> SelectOrderByStep<*>.fetchPage(
    countQuery: Select<out Record1<Int>>,
    pageRequest: PageRequest,
    orderBy: List<SortField<*>>,
    type: Class<T>,
): PagedResult<T> {
    val result = orderBy(orderBy)
        .limit(pageRequest.size)
        .offset(pageRequest.offset)
        .fetchInto(type)
    val totalCount = if (result.isNotEmpty() && result.size < pageRequest.size) {
        pageRequest.offset + result.size
    } else {
        checkNotNull(countQuery.fetchOne()).value1().toLong()
    }
    return PagedResult.of(result, pageRequest, totalCount)
}

fun <R : Record> List<Select<R>>.unionAll(): Select<R> = reduce { union, select -> union.unionAll(select) }

fun <R : TableRecord<R>> InsertResultStep<R>.fetchOneOrThrow(): R =
    fetchOne() ?: throw NoDataFoundException()

fun <R : TableRecord<R>> UpdateResultStep<R>.fetchOneOrThrow(): R =
    fetchOne() ?: throw NoDataFoundException()

/**
 * Runs the block inside a transaction, and if a DataAccessException is thrown, unwraps its cause and rethrows it.
 */
fun <R : Any?> DSLContext.transactionResultWithError(block: (DSLContext) -> R): R {
    return try {
        this.transactionResult { config -> block(config.dsl()) }
    } catch (ex: DataAccessException) {
        // Cause is encapsulated in the DataAccessException inside a transaction, so we need to unwrap it again here
        // because we're interested in the DuplicationErrors on the call site
        throw ex.cause ?: ex
    }
}
