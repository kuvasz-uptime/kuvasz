package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.common.tooltipText
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageListUiTest(
    private val appGlobals: AppGlobals,
    private val pushMonitorRepository: PushMonitorRepository,
) : UiTestSpec() {
    init {
        val defaultPage = appGlobals.defaultStatusPageSettings

        "the status pages can be searched by their title and filtered by their visibility" {
            createStatusPage(dslContext, title = "Acme public", public = true)
            createStatusPage(dslContext, title = "Acme private", public = false)
            createStatusPage(dslContext, title = "Other", public = true)

            val page = newPage()
            val list = StatusPageListPage(page)
            val filters = list.pagedList
            list.navigate()
            assertThat(list.rows).hasCount(FILTERED_PAGE_COUNT)
            assertThat(list.defaultRow).isVisible()
            assertThat(filters.filter("name")).not().isVisible()

            filters.openFilters()
            filters.searchByName("acme")
            filters.expectQuery("/status-pages", "name=acme")
            assertThat(list.rows).hasCount(2)
            // The default page is listed only as long as it matches the filters too
            assertThat(list.defaultRow).hasCount(0)

            val otherVisibility = if (defaultPage.public) Messages.no() else Messages.yes()
            filters.clearFiltersButton.click()
            filters.choose("public", otherVisibility)
            filters.expectQuery("/status-pages", "public=${!defaultPage.public}")
            assertThat(list.rows).hasCount(if (defaultPage.public) 1 else 2)
            assertThat(list.defaultRow).hasCount(0)

            filters.searchByName("nothing like this")
            assertThat(page.getByTestId("empty-state")).containsText(Messages.noMatchingStatusPages())
        }

        "the default status page stays on top of the first page, whatever the order is" {
            createStatusPage(dslContext, title = "Alpha", slug = "zulu")
            createStatusPage(dslContext, title = "Bravo", slug = "yankee")

            val page = newPage()
            val list = StatusPageListPage(page)
            val sorting = list.pagedList
            val titles = page.locator("#status-page-list tbody tr td:first-of-type")
            list.navigate()
            // The title of the default page is followed by its badge, so they are matched by what they contain
            assertThat(titles).containsText(arrayOf(defaultPage.title, "Alpha", "Bravo"))
            sorting.expectSortState("TITLE", "ascending")

            sorting.sortHeader("TITLE").click()
            sorting.expectQuery("/status-pages", "sortedBy=TITLE&sortOrder=DESC")
            assertThat(titles).containsText(arrayOf(defaultPage.title, "Bravo", "Alpha"))

            sorting.sortHeader("SLUG").click()
            sorting.expectQuery("/status-pages", "sortedBy=SLUG")
            assertThat(titles).containsText(arrayOf(defaultPage.title, "Bravo", "Alpha"))

            sorting.sortHeader("ID").click()
            sorting.expectQuery("/status-pages", "sortedBy=ID")
            assertThat(titles).containsText(arrayOf(defaultPage.title, "Alpha", "Bravo"))
        }

        "the status pages are paged, and the default one is shown on the first page only" {
            for (index in 1..PAGE_COUNT) {
                createStatusPage(dslContext, title = "Page ${"$index".padStart(2, '0')}")
            }

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            assertThat(list.rows).hasCount(PageRequest.DEFAULT_SIZE)
            assertThat(list.defaultRow).isVisible()
            // The default page is not one of the stored ones, so it's not counted
            assertThat(list.pagedList.paginationSummary)
                .hasText(Messages.listPaginationSummary(1, PageRequest.DEFAULT_SIZE, PAGE_COUNT))

            list.pagedList.pageLink(2).click()
            list.pagedList.expectQuery("/status-pages", "page=2")
            assertThat(list.rows).hasCount(PAGE_COUNT - PageRequest.DEFAULT_SIZE)
            assertThat(list.defaultRow).hasCount(0)
        }

        "the default status page is shown as unavailable above the limit of its monitors" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            assertThat(list.defaultRowPreviewLink).isVisible()
            assertThat(list.defaultRowUnavailableBadge).hasCount(0)

            repeat(StatusPageDefaults.DEFAULT_PAGE_MAX_MONITORS + 1) {
                createPushMonitor(pushMonitorRepository)
            }
            list.navigate()

            assertThat(list.defaultRowUnavailableBadge).hasText(Messages.unavailable())
            list.defaultRowUnavailableBadge.locator(".badge").tooltipText() shouldBe
                Messages.defaultStatusPageUnavailableDescription(StatusPageDefaults.DEFAULT_PAGE_MAX_MONITORS)
            assertThat(list.defaultRowUnavailablePreview).isDisabled()
            assertThat(list.defaultRowPreviewLink).hasCount(0)
        }
    }

    private companion object {
        const val PAGE_COUNT = PageRequest.DEFAULT_SIZE + 2
        const val FILTERED_PAGE_COUNT = 3
    }
}
