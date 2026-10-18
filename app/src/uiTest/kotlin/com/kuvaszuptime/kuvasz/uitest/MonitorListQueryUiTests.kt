package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.uitest.pages.common.MonitorListView
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

private const val ASCENDING = "ascending"
private const val DESCENDING = "descending"

// More than a page of the default size, but less than two
private const val PAGED_MONITOR_COUNT = PageRequest.DEFAULT_SIZE + 2
private const val LARGE_PAGE_SIZE = 50
private const val SMALL_PAGE_SIZE = 10
private const val LAST_SMALL_PAGE = 3
private const val FIRST_NAME_WITH_TWO_TENS = 20
private const val OLDER_STATUS_MINUTES = 30L
private const val NEWER_STATUS_MINUTES = 10L

/**
 * The tests of the filters, the sorting and the paging of a monitor list, which work the same for every type: they
 * live in the generic paged list components, but every type wires them up on its own.
 */
fun UiTestSpec.monitorListQueryTests(config: MonitorListUiTestConfig) {

    "the filters of the ${config.slug} monitor list are closed at first, and every one of them narrows the list" {
        filterTest(MonitorListView(newPage(), config))
    }

    "the ${config.slug} monitor list is sorted by clicking its headers, in both directions" {
        sortingTest(MonitorListView(newPage(), config))
    }

    "the ${config.slug} monitor list is paged, and a filter change starts it over from the first page" {
        pagingTest(MonitorListView(newPage(), config))
    }

    "a link to the ${config.slug} monitor list opens it with the filters, the sorting and the page of the link" {
        permalinkTest(MonitorListView(newPage(), config))
    }
}

private fun filterTest(list: MonitorListView) {
    val allNames = listOf("alpha api", "beta", "delta", "gamma API")
    with(list.config) {
        createMonitor("alpha api", "Payments", true)
        createMonitor("beta", "Search", true)
        createMonitor("gamma API", null, false)
        createMonitor("delta", "Payments", true)
        startUptimeStatus("alpha api", UptimeStatus.UP, OffsetDateTime.now())
        startUptimeStatus("beta", UptimeStatus.DOWN, OffsetDateTime.now())
        startUptimeStatus("delta", UptimeStatus.UP, OffsetDateTime.now())
    }
    val filters = list.pagedList
    list.navigate()
    list.expectNames(allNames)
    // The footer counts the monitors, even if they fit on a single page
    assertThat(filters.paginationSummary).hasText(Messages.listPaginationSummary(1, allNames.size, allNames.size))
    assertThat(filters.filter("name")).not().isVisible()
    assertThat(filters.activeFilterCount).not().isVisible()

    filters.openFilters()
    filters.searchByName("api")
    list.expectQuery("name=api")
    list.expectNames("alpha api", "gamma API")
    assertThat(filters.activeFilterCount).hasText("1")

    filters.choose("category", "Payments")
    list.expectQuery("name=api&category=Payments")
    list.expectNames("alpha api")
    assertThat(filters.activeFilterCount).hasText("2")

    filters.clearFiltersButton.click()
    list.expectQuery()
    list.expectNames(allNames)
    assertThat(filters.activeFilterCount).not().isVisible()
    assertThat(filters.filter("name")).hasValue("")
    filters.chosenLabel("category") shouldBe Messages.allCategories()

    // The empty parameter is the monitors without a category, unlike no parameter at all
    filters.choose("category", Messages.uncategorizedMonitors())
    list.expectQuery("category=")
    list.expectNames("gamma API")

    filters.clearFiltersButton.click()
    filters.choose("enabled", Messages.no())
    list.expectQuery("enabled=false")
    list.expectNames("gamma API")

    filters.clearFiltersButton.click()
    filters.toggle("uptimeStatus", Messages.down())
    list.expectQuery("uptimeStatus=DOWN")
    list.expectNames("beta")

    filters.toggle("uptimeStatus", Messages.up())
    list.expectQuery("uptimeStatus=UP&uptimeStatus=DOWN")
    list.expectNames("alpha api", "beta", "delta")

    filters.searchByName("nothing like this")
    assertThat(list.page.getByTestId("empty-state")).containsText(Messages.noMatchingMonitors())
    assertThat(filters.pagination).hasCount(0)
}

private fun sortingTest(list: MonitorListView) {
    with(list.config) {
        createMonitor("Alpha", null, true)
        createMonitor("Bravo", null, true)
        createMonitor("Charlie", null, true)
        startUptimeStatus("Alpha", UptimeStatus.UP, OffsetDateTime.now().minusMinutes(OLDER_STATUS_MINUTES))
        startUptimeStatus("Bravo", UptimeStatus.DOWN, OffsetDateTime.now().minusMinutes(NEWER_STATUS_MINUTES))
    }
    val sorting = list.pagedList
    list.navigate()
    list.expectNames("Alpha", "Bravo", "Charlie")
    sorting.expectSortState("NAME", ASCENDING)

    sorting.sortHeader("NAME").click()
    list.expectQuery("sortedBy=NAME&sortOrder=DESC")
    list.expectNames("Charlie", "Bravo", "Alpha")
    sorting.expectSortState("NAME", DESCENDING)

    // Back to the default
    sorting.sortHeader("NAME").click()
    list.expectQuery()
    list.expectNames("Alpha", "Bravo", "Charlie")

    // A monitor without a status yet comes last, whatever the order is
    sorting.sortHeader("UPTIME_STATUS").click()
    list.expectQuery("sortedBy=UPTIME_STATUS")
    list.expectNames("Alpha", "Bravo", "Charlie")
    sorting.expectSortState("UPTIME_STATUS", ASCENDING)
    sorting.sortStateOf("NAME") shouldBe null

    sorting.sortHeader("UPTIME_STATUS").click()
    list.expectQuery("sortedBy=UPTIME_STATUS&sortOrder=DESC")
    list.expectNames("Bravo", "Alpha", "Charlie")

    sorting.sortHeader("UPTIME_STATUS_STARTED_AT").click()
    list.expectQuery("sortedBy=UPTIME_STATUS_STARTED_AT")
    list.expectNames("Alpha", "Bravo", "Charlie")

    sorting.sortHeader("UPTIME_STATUS_STARTED_AT").click()
    list.expectQuery("sortedBy=UPTIME_STATUS_STARTED_AT&sortOrder=DESC")
    list.expectNames("Bravo", "Alpha", "Charlie")

    list.config.otherSortFields.forEach { field ->
        sorting.sortHeader(field).click()
        list.expectQuery("sortedBy=$field")
        sorting.expectSortState(field, ASCENDING)
        sorting.sortHeader(field).click()
        list.expectQuery("sortedBy=$field&sortOrder=DESC")
        sorting.expectSortState(field, DESCENDING)
    }
}

private fun pagingTest(list: MonitorListView) {
    for (index in 1..PAGED_MONITOR_COUNT) {
        list.config.createMonitor("Monitor ${"$index".padStart(2, '0')}", null, true)
    }
    val paging = list.pagedList
    list.navigate()
    assertThat(list.rows).hasCount(PageRequest.DEFAULT_SIZE)
    assertThat(paging.paginationSummary)
        .hasText(Messages.listPaginationSummary(1, PageRequest.DEFAULT_SIZE, PAGED_MONITOR_COUNT))
    // The pages are permalinks too, e.g. to open them in a new tab
    assertThat(paging.pageLink(2)).hasAttribute("href", "${list.config.listPath}?page=2")

    paging.pageLink(2).click()
    list.expectQuery("page=2")
    list.expectNames(namesOfMonitors(PageRequest.DEFAULT_SIZE + 1..PAGED_MONITOR_COUNT))
    assertThat(paging.paginationSummary)
        .hasText(Messages.listPaginationSummary(PageRequest.DEFAULT_SIZE + 1, PAGED_MONITOR_COUNT, PAGED_MONITOR_COUNT))

    paging.previousPageLink().click()
    list.expectQuery()
    assertThat(list.rows).hasCount(PageRequest.DEFAULT_SIZE)

    paging.setPageSize(LARGE_PAGE_SIZE)
    list.expectQuery("size=$LARGE_PAGE_SIZE")
    assertThat(list.rows).hasCount(PAGED_MONITOR_COUNT)
    assertThat(paging.pageLink(2)).hasCount(0)

    paging.setPageSize(SMALL_PAGE_SIZE)
    list.expectQuery("size=$SMALL_PAGE_SIZE")
    paging.pageLink(LAST_SMALL_PAGE).click()
    list.expectQuery("page=$LAST_SMALL_PAGE&size=$SMALL_PAGE_SIZE")
    assertThat(list.rows).hasCount(PAGED_MONITOR_COUNT - 2 * SMALL_PAGE_SIZE)

    paging.openFilters()
    paging.searchByName("Monitor 2")
    list.expectQuery("name=Monitor+2&size=$SMALL_PAGE_SIZE")
    list.expectNames(namesOfMonitors(FIRST_NAME_WITH_TWO_TENS..PAGED_MONITOR_COUNT))
}

private fun namesOfMonitors(numbers: IntRange): List<String> = numbers.map { "Monitor $it" }

private fun permalinkTest(list: MonitorListView) {
    val categoriesByName = mapOf(
        "api one" to "Payments",
        "api two" to "Payments",
        "api three" to "Search",
        "web" to "Payments",
    )
    categoriesByName.forEach { (name, category) -> list.config.createMonitor(name, category, true) }
    val state = list.pagedList
    val query = "name=api&category=Payments&sortedBy=NAME&sortOrder=DESC"
    list.navigate(query)
    list.expectNames("api two", "api one")
    assertThat(state.activeFilterCount).hasText("2")
    state.expectSortState("NAME", DESCENDING)

    state.openFilters()
    assertThat(state.filter("name")).hasValue("api")
    state.chosenLabel("category") shouldBe "Payments"

    // The list keeps the URL as it is, so it can be reloaded or shared
    list.expectQuery(query)
    list.page.reload()
    list.expectNames("api two", "api one")

    // A page past the end shows the last one instead
    list.navigate("page=$LAST_SMALL_PAGE")
    list.expectQuery()
    assertThat(list.rows).hasCount(categoriesByName.size)
}
