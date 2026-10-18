package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.ui.components.*

private const val CATEGORY_PARAM = "category"

/**
 * The filters every monitor list has. [availableCategories] are the ones offered by the category filter, which is
 * only needed where the filter is rendered.
 */
class MonitorListFilters internal constructor(params: QueryParams, availableCategories: List<String>) : ListFilters {

    val name: TextFilter = textFilter(params, "name", Messages.name(), placeholder = Messages.searchByName())

    val category: ChoiceFilter<CategoryFilter> = categoryFilter(params, availableCategories)

    val enabled: ChoiceFilter<Boolean> = yesNoFilter(params, "enabled", Messages.enabled())

    val uptimeStatus: MultiChoiceFilter<UptimeStatus> = multiChoiceFilter(
        params,
        "uptimeStatus",
        Messages.uptimeStatusFilterLabel(),
        enumOptions(UptimeStatus.entries) { status ->
            when (status) {
                UptimeStatus.UP -> Messages.up()
                UptimeStatus.DOWN -> Messages.down()
            }
        },
    )

    override val all: List<ListFilter> = listOf(name, category, enabled, uptimeStatus)
}

/** The filters of the HTTP monitor list: the ones of [monitor], plus the ones of the SSL checks. */
class HttpMonitorListFilters internal constructor(params: QueryParams, availableCategories: List<String>) :
    ListFilters {

    val monitor = MonitorListFilters(params, availableCategories)

    val sslStatus: MultiChoiceFilter<SslStatus> = multiChoiceFilter(
        params,
        "sslStatus",
        Messages.sslStatus(),
        enumOptions(SslStatus.entries) { status ->
            when (status) {
                SslStatus.VALID -> Messages.valid()
                SslStatus.INVALID -> Messages.invalid()
                SslStatus.WILL_EXPIRE -> Messages.expiresSoon()
            }
        },
    )

    val sslCheckEnabled: ChoiceFilter<Boolean> = yesNoFilter(params, "sslCheckEnabled", Messages.sslCheckSwitchLabel())

    override val all: List<ListFilter> = monitor.all + listOf(sslStatus, sslCheckEnabled)
}

/**
 * Mirrors the `category` parameter of the v3 API, see [CategoryFilter]. A category that isn't among the available
 * ones (e.g. one that no monitor of the type has anymore) is still offered, so a link to it keeps working.
 */
private fun categoryFilter(params: QueryParams, availableCategories: List<String>): ChoiceFilter<CategoryFilter> {
    val requested = CategoryFilter.fromQueryParam(params(CATEGORY_PARAM).firstOrNull())
    val categories = (availableCategories + listOfNotNull((requested as? CategoryFilter.InCategory)?.category))
        .distinct()
        .sortedBy { it.lowercase() }
    val options = categories.map { FilterOption(CategoryFilter.InCategory(it), queryValue = it, label = it) } +
        FilterOption(CategoryFilter.Uncategorized, queryValue = "", label = Messages.uncategorizedMonitors())
    return ChoiceFilter(
        param = CATEGORY_PARAM,
        label = Messages.monitorCategoryLabel(),
        anyLabel = Messages.allCategories(),
        options = options,
        selected = options.firstOrNull { it.value == requested },
    )
}

/** The state of a monitor list with the [filters] of its type. */
internal fun <F : Enum<F>, L : ListFilters> monitorListQuery(
    params: QueryParams,
    typeUiConfig: MonitorTypeUiConfig,
    filters: L,
    sortFields: List<F>,
    defaultSorting: Sorting<F>,
): ListQuery<F, L> = ListQuery.parse(
    params = params,
    path = typeUiConfig.listPath,
    filters = filters,
    sortFields = sortFields,
    defaultSorting = defaultSorting,
)
