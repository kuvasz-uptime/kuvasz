package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.ui.utils.*

// The values of a query parameter of a request by its name, none if it's absent
typealias QueryParams = (String) -> List<String>

/**
 * A filter of a paged list. Its value travels in the query parameter [param], both in the URL of the list page and in
 * the requests of the list itself. [queryValues] are the values of the parameter, none if the filter isn't set.
 */
sealed class ListFilter(val param: String, val label: String, val queryValues: List<String>) {

    val isActive: Boolean = queryValues.isNotEmpty()

    // The value of the filter in the state of the listQuery Alpine.js component, see kuvasz.js
    internal abstract val stateValue: Any?

    // The stateValue of the filter when it isn't set
    internal abstract val emptyStateValue: Any?
}

/** A free text filter, e.g. a search by name. A blank text is no filter. */
class TextFilter internal constructor(
    param: String,
    label: String,
    val placeholder: String,
    val value: String?,
) : ListFilter(param, label, listOfNotNull(value)) {

    override val stateValue: Any = value.orEmpty()

    override val emptyStateValue: Any = ""
}

/** A filter of a whole number, e.g. an ID. Anything but a positive number is no filter. */
class NumberFilter internal constructor(
    param: String,
    label: String,
    val value: Long?,
) : ListFilter(param, label, listOfNotNull(value?.toString())) {

    override val stateValue: Any = value?.toString().orEmpty()

    override val emptyStateValue: Any = ""
}

/** A choice of the filters with options: [queryValue] is how [value] appears in the URL. */
class FilterOption<out V>(val value: V, val queryValue: String, val label: String)

/** A filter of one of its [options], or of none of them, which is the first choice, labeled [anyLabel]. */
class ChoiceFilter<out V> internal constructor(
    param: String,
    label: String,
    val anyLabel: String,
    val options: List<FilterOption<V>>,
    val selected: FilterOption<V>?,
) : ListFilter(param, label, listOfNotNull(selected?.queryValue)) {

    val value: V? = selected?.value

    override val stateValue: Any? = selected?.queryValue

    override val emptyStateValue: Any? = null
}

/**
 * A filter of one of its [options], which always has a value: without the parameter, it's the one of [default]. Unlike
 * a [ChoiceFilter], it has no choice for "any" of the options, e.g. the period of a list.
 */
class DefaultedChoiceFilter<out V> internal constructor(
    param: String,
    label: String,
    val options: List<FilterOption<V>>,
    val default: FilterOption<V>,
    val selected: FilterOption<V>,
) : ListFilter(param, label, listOfNotNull(selected.takeIf { it != default }?.queryValue)) {

    val value: V = selected.value

    override val stateValue: Any? = queryValues.firstOrNull()

    override val emptyStateValue: Any? = null
}

/** A filter of any number of its [options], every one of them is a value of the parameter. */
class MultiChoiceFilter<out V> internal constructor(
    param: String,
    label: String,
    val options: List<FilterOption<V>>,
    val selected: List<FilterOption<V>>,
) : ListFilter(param, label, selected.map { it.queryValue }) {

    val values: List<V> = selected.map { it.value }

    override val stateValue: Any? = queryValues

    override val emptyStateValue: Any? = emptyList<String>()
}

internal fun textFilter(params: QueryParams, param: String, label: String, placeholder: String) =
    TextFilter(param, label, placeholder, value = params(param).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() })

internal fun numberFilter(params: QueryParams, param: String, label: String) =
    NumberFilter(param, label, value = params(param).firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0 })

// The case of the values doesn't matter, and a value that isn't one of the options is ignored, as if it wasn't there
internal fun <V> choiceFilter(
    params: QueryParams,
    param: String,
    label: String,
    anyLabel: String,
    options: List<FilterOption<V>>,
): ChoiceFilter<V> {
    val requested = params(param).firstOrNull()
    return ChoiceFilter(param, label, anyLabel, options, selected = options.firstMatching(requested))
}

internal fun <V> multiChoiceFilter(
    params: QueryParams,
    param: String,
    label: String,
    options: List<FilterOption<V>>,
): MultiChoiceFilter<V> {
    val requested = params(param)
    return MultiChoiceFilter(
        param,
        label,
        options,
        selected = options.filter { option -> requested.any { option.queryValue.equals(it, ignoreCase = true) } },
    )
}

internal fun <V> defaultedChoiceFilter(
    params: QueryParams,
    param: String,
    label: String,
    options: List<FilterOption<V>>,
    default: FilterOption<V>,
): DefaultedChoiceFilter<V> = DefaultedChoiceFilter(
    param,
    label,
    options,
    default,
    selected = options.firstMatching(params(param).firstOrNull()) ?: default,
)

private fun <V> List<FilterOption<V>>.firstMatching(queryValue: String?): FilterOption<V>? =
    firstOrNull { it.queryValue.equals(queryValue, ignoreCase = true) }

internal fun yesNoOptions(): List<FilterOption<Boolean>> = listOf(
    FilterOption(true, queryValue = "true", label = Messages.yes()),
    FilterOption(false, queryValue = "false", label = Messages.no()),
)

internal fun yesNoFilter(params: QueryParams, param: String, label: String): ChoiceFilter<Boolean> =
    choiceFilter(
        params,
        param,
        label,
        anyLabel = Messages.anyValue(),
        options = yesNoOptions(),
    )

internal fun <E : Enum<E>> enumOptions(values: List<E>, label: (E) -> String): List<FilterOption<E>> =
    values.map { FilterOption(it, queryValue = it.name, label = label(it)) }

/** The filters of a paged list, in the order they are shown. */
interface ListFilters {
    val all: List<ListFilter>
}

/**
 * The state of a paged list: its [filters], its [sorting] and the page shown, as they travel in the query parameters
 * of the list page at [path] and of the requests of the list itself, see listQuery in kuvasz.js. The parameters are
 * named after the ones of the v3 API.
 *
 * The URLs built from it leave out the parameters with a default value, so the list without any of them keeps its
 * plain [path].
 */
class ListQuery<F : Enum<F>, out L : ListFilters> private constructor(
    val path: String,
    val filters: L,
    val sorting: Sorting<F>,
    val pageRequest: PageRequest,
    private val defaultSorting: Sorting<F>,
) {

    val activeFilterCount: Int = filters.all.count { it.isActive }

    private val navigationParams: Map<String, String> = navigationParamsOf(sorting, pageRequest)

    val url: String = urlOf(path, navigationParams)

    /** The same state on another page, e.g. on the last one, when the requested page is past the end of the list. */
    fun withPage(page: Int): ListQuery<F, L> =
        ListQuery(path, filters, sorting, pageRequest.copy(page = page), defaultSorting)

    /**
     * The URL of the list sorted by [field]: in the opposite order if it's already sorted by it, ascending otherwise,
     * starting from the first page.
     */
    fun sortedByUrl(field: F): String {
        val order = if (sorting.field == field && sorting.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
        return urlOf(path, navigationParamsOf(Sorting(field, order), pageRequest.copy(page = PageRequest.DEFAULT_PAGE)))
    }

    fun pageUrl(page: Int): String = urlOf(path, navigationParamsOf(sorting, pageRequest.copy(page = page)))

    /** The URL of the list's own requests, at [fragmentPath], with the same state. */
    internal fun fragmentUrl(fragmentPath: String): String = urlOf(fragmentPath, navigationParams)

    /** The initial state of the listQuery Alpine.js component of the list, see kuvasz.js */
    internal fun alpineState(): String = mapOf(
        "filters" to filters.all.associate { it.param to it.stateValue },
        "emptyFilters" to filters.all.associate { it.param to it.emptyStateValue },
        "navigation" to NAVIGATION_PARAMS.associateWith { navigationParams[it] },
    ).asJsonString()

    private fun navigationParamsOf(sorting: Sorting<F>, pageRequest: PageRequest): Map<String, String> = buildMap {
        if (sorting != defaultSorting) {
            put(SORTED_BY_PARAM, sorting.field.name)
            // Without the order, a field is sorted ascending
            if (sorting.order == SortOrder.DESC) put(SORT_ORDER_PARAM, SortOrder.DESC.name)
        }
        if (pageRequest.page != PageRequest.DEFAULT_PAGE) put(PAGE_PARAM, pageRequest.page.toString())
        if (pageRequest.size != PageRequest.DEFAULT_SIZE) put(SIZE_PARAM, pageRequest.size.toString())
    }

    private fun urlOf(path: String, navigationParams: Map<String, String>): String {
        val params = filters.all.flatMap { filter -> filter.queryValues.map { filter.param to it } } +
            navigationParams.toList()
        return if (params.isEmpty()) {
            path
        } else {
            params.joinToString("&", prefix = "$path?") { (name, value) -> "$name=${value.urlEncode()}" }
        }
    }

    companion object {
        internal const val PAGE_PARAM = "page"
        internal const val SIZE_PARAM = "size"
        private const val SORTED_BY_PARAM = "sortedBy"
        private const val SORT_ORDER_PARAM = "sortOrder"

        // The parameters that the sort headers, the pages and the page sizes change, while the filters are kept
        private val NAVIGATION_PARAMS = listOf(PAGE_PARAM, SIZE_PARAM, SORTED_BY_PARAM, SORT_ORDER_PARAM)

        val PAGE_SIZES = listOf(10, PageRequest.DEFAULT_SIZE, 50, 100)

        /**
         * The state of a list from the query parameters of a request. It's parsed leniently, as the URL can be edited
         * by hand: a value that is unknown or out of range is ignored, as if it wasn't there.
         */
        internal fun <F : Enum<F>, L : ListFilters> parse(
            params: QueryParams,
            path: String,
            filters: L,
            sortFields: List<F>,
            defaultSorting: Sorting<F>,
        ): ListQuery<F, L> {
            fun firstOf(param: String) = params(param).firstOrNull()
            val sorting = Sorting.of(
                field = sortFields.firstOrNull { it.name.equals(firstOf(SORTED_BY_PARAM), ignoreCase = true) },
                order = SortOrder.entries.firstOrNull { it.name.equals(firstOf(SORT_ORDER_PARAM), ignoreCase = true) },
                default = defaultSorting,
            )
            val pageRequest = PageRequest(
                page = firstOf(PAGE_PARAM)?.toIntOrNull()?.takeIf { it >= PageRequest.DEFAULT_PAGE }
                    ?: PageRequest.DEFAULT_PAGE,
                size = firstOf(SIZE_PARAM)?.toIntOrNull()?.takeIf { it in PAGE_SIZES } ?: PageRequest.DEFAULT_SIZE,
            )
            return ListQuery(path, filters, sorting, pageRequest, defaultSorting)
        }
    }
}
