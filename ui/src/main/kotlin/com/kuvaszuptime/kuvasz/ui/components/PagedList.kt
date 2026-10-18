package com.kuvaszuptime.kuvasz.ui.components

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration

// Makes the list of a listQuery Alpine.js component load again with the current state, see kuvasz.js
private const val LIST_QUERY_CHANGE_EVENT = "list-query-change"

/**
 * A paged list in a card: the filter panel of the list, and the list itself, which is loaded from [fragmentPath] into
 * [listElementId] by htmx, and refreshed on [refreshEvent], and every [refreshInterval] if there is one.
 *
 * The state of the list (its filters, sorting and page) is owned by the listQuery Alpine.js component of the card (see
 * kuvasz.js), and every request of the list is made with it. The first one is also rendered with it here, in case the
 * list is requested before the component is initialized.
 */
internal fun FlowContent.pagedListCard(
    query: ListQuery<*, *>,
    listElementId: String,
    fragmentPath: String,
    refreshEvent: String,
    refreshInterval: Duration?,
) {
    div {
        classes(CARD)
        xData("listQuery(${query.alpineState()})")
        listFilterPanel(listElementId, query)
        pagedList(query, listElementId, fragmentPath, refreshEvent, refreshInterval)
    }
}

/**
 * The element a paged list is loaded into, which has to be inside the listQuery Alpine.js component of the list, see
 * [pagedListCard].
 */
internal fun FlowContent.pagedList(
    query: ListQuery<*, *>,
    listElementId: String,
    fragmentPath: String,
    refreshEvent: String?,
    refreshInterval: Duration?,
    listClasses: Set<CSSClass> = emptySet(),
) {
    div {
        id = listElementId
        if (listClasses.isNotEmpty()) classes(listClasses)
        xRef("list")
        hx {
            get(query.fragmentUrl(fragmentPath))
            trigger {
                load()
                refreshEvent?.let { event(it) }
                event(LIST_QUERY_CHANGE_EVENT)
                refreshInterval?.let { every(it) }
            }
            // A change of the state aborts the request with the previous one, instead of being dropped by htmx
            sync("this:replace")
            onSwapReinitTooltips()
        }
        xOn("htmx:config-request.self", "configureRequest(\$event)")
        xOn("htmx:after-swap.self", "syncFromList(\$el)")
        htmxLoadingIndicator()
    }
}
