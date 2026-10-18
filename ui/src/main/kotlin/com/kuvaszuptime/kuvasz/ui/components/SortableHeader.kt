package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The header of a column of a paged list that can be sorted by [field]. It links to the URL of the list sorted by it
 * (see [ListQuery.sortedByUrl]), which a plain click loads in place, see listQuery in kuvasz.js.
 */
internal fun <F : Enum<F>> TR.sortableHeader(
    query: ListQuery<F, *>,
    field: F,
    label: String,
    headerClasses: Set<CSSClass> = emptySet(),
) {
    val order = query.sorting.order.takeIf { query.sorting.field == field }
    th {
        classes(headerClasses)
        order?.let { attributes["aria-sort"] = if (it == SortOrder.ASC) "ascending" else "descending" }
        a(href = query.sortedByUrl(field)) {
            classes(mutableSetOf(TABLE_SORT).addIfNotNull(order?.let { if (it == SortOrder.ASC) ASC else DESC }))
            testId("sort-by-${field.name}")
            xOnClick("followLink(\$event)")
            +label
        }
    }
}
