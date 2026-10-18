package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.kuvaszuptime.kuvasz.uitest.MonitorListUiTestConfig
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/** A monitor list of any type, by its [MonitorListUiTestConfig]. */
class MonitorListView(val page: Page, val config: MonitorListUiTestConfig) {

    val pagedList = PagedListView(page, "${config.slug}-monitors-list")

    val rows: Locator get() = page.getByTestId("${config.slug}-monitor-row")

    fun navigate(query: String = "") {
        page.navigate(if (query.isEmpty()) config.listPath else "${config.listPath}?$query")
    }

    // Waits for the rows to be the ones with the given names, in this order: a swap of the list follows the change of
    // the URL, so the rows have to be waited for
    fun expectNames(names: List<String>) {
        assertThat(rows.locator("td:first-of-type")).hasText(names.toTypedArray())
    }

    fun expectNames(vararg names: String) = expectNames(names.toList())

    fun expectQuery(query: String = "") = pagedList.expectQuery(config.listPath, query)
}
