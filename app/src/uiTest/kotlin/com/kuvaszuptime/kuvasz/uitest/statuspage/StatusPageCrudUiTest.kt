package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageCrudUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a status page can be created and edited through the UI via an editable upsert modal" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val title = "E2E Status Page"
            list.openCreateModal()
                .setTitle(title)
                .setSlug("e2e-status-page")
                .save()
            page.waitForURL("**/status-pages/*")
            val details = StatusPageDetailsPage(page)
            assertThat(details.heading(title)).isVisible()
            assertThat(details.content).isVisible()
            assertThat(details.configureButton).isVisible()

            // Contrast with the read-only case: the upsert modal here is editable, with Save available.
            val updatedTitle = "E2E Status Page Renamed"
            val modal = details.openConfigureModal()
            assertThat(modal.titleInput).isEnabled()
            assertThat(modal.saveButton).isVisible()
            modal.setTitle(updatedTitle).save()
            assertThat(details.heading(updatedTitle)).isVisible()
        }

        "a status page can be edited from its list row, and saving it leads back to the list" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Listed Monitor")
            createStatusPage(
                dslContext,
                title = "List Edit Source",
                slug = "list-edit-source",
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "Listed Monitor")),
                categories = listOf("Payments"),
            )
            createStatusPage(dslContext, title = "Neighbour Page", slug = "neighbour-page")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.configureStatusPage("List Edit Source")
            assertThat(modal.title).hasText(Messages.updateStatusPage("List Edit Source"))
            // Every value is loaded from the status page of the row
            assertThat(modal.titleInput).hasValue("List Edit Source")
            assertThat(modal.titleInput).isEnabled()
            assertThat(modal.slugInput).hasValue("list-edit-source")
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Listed Monitor")
            assertThat(modal.selectedCategories).hasCount(1)
            assertThat(modal.selectedCategories).containsText("Payments")

            modal.setTitle("List Edit Renamed").setSlug("list-edit-renamed")
            modal.clearCategories()
            modal.save()

            // The list is reloaded in place, instead of navigating to the details page of the status page
            assertThat(list.rowByTitle("List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/status-pages"
            // ...and the status page was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByTitle("List Edit Source")).hasCount(0)
            assertThat(list.rowByTitle("List Edit Renamed")).containsText("/status/list-edit-renamed")
            assertThat(list.categoriesCell("List Edit Renamed")).hasText("0")
            assertThat(list.rowByTitle("Neighbour Page")).containsText("/status/neighbour-page")
        }

        "the list's modal loads the status page of each row, discards abandoned edits and still creates new ones" {
            createStatusPage(
                dslContext,
                title = "First Row Page",
                slug = "first-row-page",
                categories = listOf("Payments"),
            )
            createStatusPage(dslContext, title = "Second Row Page", slug = "second-row-page")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val first = list.configureStatusPage("First Row Page")
            assertThat(first.titleInput).hasValue("First Row Page")
            first.setTitle("Abandoned Title").setSlug("abandoned-slug").dismiss()

            val second = list.configureStatusPage("Second Row Page")
            assertThat(second.title).hasText(Messages.updateStatusPage("Second Row Page"))
            assertThat(second.titleInput).hasValue("Second Row Page")
            assertThat(second.slugInput).hasValue("second-row-page")
            assertThat(second.selectedCategories).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureStatusPage("First Row Page")
            assertThat(firstAgain.titleInput).hasValue("First Row Page")
            assertThat(firstAgain.slugInput).hasValue("first-row-page")
            assertThat(firstAgain.selectedCategories).hasCount(1)
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new status page
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewStatusPage())
            assertThat(create.titleInput).hasValue("")
            assertThat(create.slugInput).hasValue("")
            assertThat(create.selectedCategories).hasCount(0)
            create.setTitle("Created After Edits").setSlug("created-after-edits").save()
            page.waitForURL("**/status-pages/*")
            assertThat(StatusPageDetailsPage(page).heading("Created After Edits")).isVisible()

            // None of the status pages opened on the way were changed
            list.navigate()
            assertThat(list.rowByTitle("Created After Edits")).isVisible()
            assertThat(list.rowByTitle("First Row Page")).containsText("/status/first-row-page")
            assertThat(list.rowByTitle("Abandoned Title")).hasCount(0)
        }

        "a status page can be cloned from its list row, pre-filling a create form under a new title and slug" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Cloned Monitor")
            createStatusPage(
                dslContext,
                title = "Clone Source Page",
                slug = "clone-source-page",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "Cloned Monitor")),
                categories = listOf("Payments"),
                displayCategories = false,
            )

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val clonedTitle = Messages.clonedStatusPageTitle("Clone Source Page")
            val modal = list.cloneStatusPage("Clone Source Page")
            assertThat(modal.title).hasText(Messages.createNewStatusPage())
            // Every value is copied from the source, except the title and the slug, which has to be unique, and the
            // visibility, as a copy starts private
            assertThat(modal.titleInput).hasValue(clonedTitle)
            assertThat(modal.slugInput).hasValue("clone-source-page-copy")
            assertThat(modal.publicToggle).not().isChecked()
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Cloned Monitor")
            assertThat(modal.selectedCategories).hasCount(1)
            assertThat(modal.selectedCategories).containsText("Payments")
            assertThat(modal.displayCategoriesToggle).not().isChecked()

            modal.save()
            page.waitForURL("**/status-pages/*")
            assertThat(StatusPageDetailsPage(page).heading(clonedTitle)).isVisible()

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByTitle(clonedTitle)).containsText("/status/clone-source-page-copy")
            assertThat(list.categoriesCell(clonedTitle)).hasText("1")
            assertThat(list.privateIndicator(clonedTitle)).isVisible()
        }

        "cloning and editing through the list's modal never mix up creating and updating a status page" {
            val longSlug = "a".repeat(SLUG_MAX_LENGTH)
            createStatusPage(dslContext, title = "Edited Page", slug = "edited-page")
            createStatusPage(dslContext, title = "Cloned Page", slug = longSlug)

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneStatusPage("Cloned Page")
            assertThat(abandonedClone.titleInput).hasValue(Messages.clonedStatusPageTitle("Cloned Page"))
            abandonedClone.dismiss()

            val edit = list.configureStatusPage("Edited Page")
            assertThat(edit.titleInput).hasValue("Edited Page")
            edit.setTitle("Edited Page Renamed").save()
            assertThat(list.rowByTitle("Edited Page Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneStatusPage("Cloned Page")
            assertThat(clone.title).hasText(Messages.createNewStatusPage())
            assertThat(clone.titleInput).hasValue(Messages.clonedStatusPageTitle("Cloned Page"))
            // The suffixed slug still fits into the maximum length of a slug
            val clonedSlug = "a".repeat(SLUG_MAX_LENGTH - CLONED_SLUG_SUFFIX.length) + CLONED_SLUG_SUFFIX
            assertThat(clone.slugInput).hasValue(clonedSlug)
            clone.save()
            page.waitForURL("**/status-pages/*")

            list.navigate()
            // The source and its copy, which didn't overwrite it
            assertThat(list.rowByTitle("Cloned Page")).hasCount(2)
            assertThat(list.rowByTitle("Cloned Page").first()).containsText("/status/$longSlug")
            assertThat(list.rowByTitle(Messages.clonedStatusPageTitle("Cloned Page")))
                .containsText("/status/$clonedSlug")
            assertThat(list.rowByTitle("Edited Page Renamed")).containsText("/status/edited-page")
        }

        "an abandoned create form is reset when the modal is reopened" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Abandoned Selection")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setTitle("Abandoned Status Page")
                .setSlug("abandoned-status-page")
            modal.selectOption("Abandoned Selection")
            assertThat(modal.selectedOptions).hasCount(1)
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.titleInput).hasValue("")
            assertThat(reopened.slugInput).hasValue("")
            // The monitor multi-select is reset along with the plain inputs.
            assertThat(reopened.selectedOptions).hasCount(0)
        }

        "edits abandoned on an existing status page are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val originalTitle = "Status Page Reset Source"
            val originalSlug = "status-page-reset-source"
            list.openCreateModal().setTitle(originalTitle).setSlug(originalSlug).save()
            page.waitForURL("**/status-pages/*")
            val details = StatusPageDetailsPage(page)

            details.openConfigureModal()
                .setTitle("Status Page Reset Renamed")
                .setSlug("changed-slug")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.titleInput).hasValue(originalTitle)
            assertThat(reopened.slugInput).hasValue(originalSlug)
        }

        "a status page can be selected by category, keeping the picked categories across a reopen" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Categorized", category = "Payments")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setTitle("Category Based Page")
                .setSlug("category-based-page")
            // The already used category is offered by the internal endpoint, a brand new one is created in place
            modal.offeredSelectableCategories shouldBe listOf("Payments")
            modal.selectCategory("Payments")
            modal.selectCategory("Brand new")
            assertThat(modal.selectedCategories).hasCount(2)
            modal.save()
            page.waitForURL("**/status-pages/*")

            val details = StatusPageDetailsPage(page)
            val reopened = details.openConfigureModal()
            assertThat(reopened.selectedCategories).hasCount(2)
            // The chips come back in the order they were picked in, which is how the array is persisted
            assertThat(reopened.selectedCategories).containsText(arrayOf("Payments", "Brand new"))
        }

        "the category display of a status page can be turned off from the form" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setTitle("Ungrouped Page")
                .setSlug("ungrouped-page")
            // On by default, so the monitors of a categorized page are grouped unless it is turned off
            assertThat(modal.displayCategoriesToggle).isChecked()
            modal.setDisplayCategories(false)
            modal.save()
            page.waitForURL("**/status-pages/*")

            val reopened = StatusPageDetailsPage(page).openConfigureModal()
            assertThat(reopened.displayCategoriesToggle).not().isChecked()
        }

        "the categories of a status page can be cleared entirely" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            list.openCreateModal()
                .setTitle("Cleared Categories")
                .setSlug("cleared-categories")
                .also { it.selectCategory("Payments") }
                .save()
            page.waitForURL("**/status-pages/*")

            val details = StatusPageDetailsPage(page)
            val modal = details.openConfigureModal()
            assertThat(modal.selectedCategories).hasCount(1)
            modal.clearCategories()
            modal.save()

            // The empty array has to reach the server, not be treated as "leave it alone"
            val reopened = details.openConfigureModal()
            assertThat(reopened.selectedCategories).hasCount(0)
        }

        "the categories of an abandoned status page form are reset when the modal is reopened" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setTitle("Abandoned Categories")
                .setSlug("abandoned-categories")
            modal.selectCategory("Payments")
            assertThat(modal.selectedCategories).hasCount(1)
            modal.dismiss()

            val reopened = list.openCreateModal()
            assertThat(reopened.selectedCategories).hasCount(0)
        }

        "the status-page modal's monitors select adds a seeded monitor" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Selectable Monitor")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("Selectable Monitor")
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Selectable Monitor")
        }

        "the status-page modal offers the monitors sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createHttpMonitor(httpMonitorRepository, monitorName = it) }

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.monitorOptions).hasCount(names.size)
            modal.monitorOptionNames shouldBe listOf("http:alpha", "http:bravo", "http:Charlie", "http:Delta")
        }

        "a status page can be published and unpublished from the list" {
            createStatusPage(
                dslContext,
                title = "List Toggle Status Page",
                slug = "list-toggle-status-page",
                public = true,
            )

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            assertThat(list.publicIndicator("List Toggle Status Page")).isVisible()
            assertThat(list.configureButtonIn("List Toggle Status Page")).isVisible()
            // The view-only configuration button is reserved for the read-only status pages
            assertThat(list.configurationButtonIn("List Toggle Status Page")).hasCount(0)

            list.toggleVisibility("List Toggle Status Page")
            assertThat(list.privateIndicator("List Toggle Status Page")).isVisible()

            list.toggleVisibility("List Toggle Status Page")
            assertThat(list.publicIndicator("List Toggle Status Page")).isVisible()
        }

        "a status page can be published and unpublished from its detail page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "SP Monitor")
            val statusPage = createStatusPage(
                dslContext,
                title = "Toggle Status Page",
                slug = "toggle-status-page",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val details = StatusPageDetailsPage(page)
            details.navigate(statusPage.id)
            assertThat(details.visibilityBadge(Messages.public())).isVisible()

            details.visibilityToggleButton.click()
            assertThat(details.visibilityBadge(Messages.private())).isVisible()
        }

        "the status page list counts the selected monitors and categories separately" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Counted Monitor")
            createStatusPage(
                dslContext,
                title = "Counted Page",
                slug = "counted-page",
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "Counted Monitor")),
                categories = listOf("Payments", "Search"),
            )

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            assertThat(list.categoriesCell("Counted Page")).hasText("2")
        }

        "the status page list is sorted by title, regardless of its casing" {
            val titles = listOf("Charlie", "bravo", "Delta", "alpha")
            titles.forEach { createStatusPage(dslContext, title = it, slug = it.lowercase()) }

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            // The table is HTMX-swapped in, so wait for every row before reading their order.
            assertThat(list.rows).hasCount(titles.size)
            list.titles shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }
    }

    companion object {
        private const val SLUG_MAX_LENGTH = 50
        private const val CLONED_SLUG_SUFFIX = "-copy"
    }
}
