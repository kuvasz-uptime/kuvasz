package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorCrudUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "an HTTP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = HttpMonitorListPage(page)

            list.navigate()
            assertThat(list.emptyState).isVisible()

            val originalName = "E2E Monitor"
            list.openCreateModal()
                .setName(originalName)
                .setUrl("https://example.com")
                .setCategory("Payments")
                .save()
            page.waitForURL("**/http-monitors/*")
            val details = HttpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            // Renaming is allowed since the monitor isn't on a status page.
            val updatedName = "E2E Monitor Renamed"
            val configureModal = details.openConfigureModal()
            // The category is pre-filled from the monitor and can be cleared
            assertThat(configureModal.selectedCategory).hasText("Payments")
            configureModal
                .setName(updatedName)
                .setCategory("")
                .save()
            assertThat(details.heading(updatedName)).isVisible()
            assertThat(details.categoryBadge).hasCount(0)

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }

        "cross-origin header propagation is disabled by default, and enabling it survives the save" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("HTTP Header Propagation Monitor")
                .setUrl("https://propagation.example.com")
                .expandRequestSettings()
            assertThat(modal.crossOriginHeaderPropagationToggle).not().isChecked()
            modal.crossOriginHeaderPropagationToggle.check()
            modal.save()
            page.waitForURL("**/http-monitors/*")

            // Re-opening the monitor's configuration must show the persisted setting
            val reopened = HttpMonitorDetailsPage(page).openConfigureModal().expandRequestSettings()
            assertThat(reopened.crossOriginHeaderPropagationToggle).isChecked()
        }

        "an HTTP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // Seed a source monitor with a couple of non-default values.
            val sourceName = "HTTP Clone Source"
            val sourceUrl = "https://clone-source.example.com"
            list.openCreateModal()
                .setName(sourceName)
                .setUrl(sourceUrl)
                .setUptimeCheckInterval("120")
                .save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            // Every field is pre-filled from the source, except the name which is suggested as "Copy of <name>".
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.urlInput).hasValue(sourceUrl)
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("120")

            cloneModal.save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "the category field offers the already existing categories and takes a brand new one" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // Two monitors seeding the categories the field is expected to offer afterwards
            list.openCreateModal()
                .setName("Category Source 1")
                .setUrl("https://category-source-1.example.com")
                .setCategory("Payments")
                .save()
            page.waitForURL("**/http-monitors/*")
            list.navigate()
            list.openCreateModal()
                .setName("Category Source 2")
                .setUrl("https://category-source-2.example.com")
                .setCategory("alerting")
                .save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            val modal = list.openCreateModal()
            // Ordered case-insensitively by the endpoint backing the autocomplete
            modal.offeredCategories shouldBe listOf("alerting", "Payments")

            // A category that doesn't exist yet is created right in the field
            modal.setName("Category Creator")
                .setUrl("https://category-creator.example.com")
                .setCategory("Drive storage")
            assertThat(modal.selectedCategory).hasText("Drive storage")
            modal.save()
            page.waitForURL("**/http-monitors/*")
            assertThat(HttpMonitorDetailsPage(page).categoryBadge).containsText("Drive storage")

            // ...and it is offered from then on
            list.navigate()
            list.openCreateModal().offeredCategories shouldBe listOf("alerting", "Drive storage", "Payments")
        }

        "the HTTP monitor modal's accepted-status-codes select adds a chosen code" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal().expandEvaluationSettings()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("200")
            assertThat(modal.selectedOptions).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned HTTP Monitor")
                .setUrl("https://abandoned.example.com")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.urlInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val originalName = "HTTP Reset Source"
            val originalUrl = "https://original.example.com"
            list.openCreateModal().setName(originalName).setUrl(originalUrl).save()
            page.waitForURL("**/http-monitors/*")
            val details = HttpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("HTTP Reset Renamed")
                .setUrl("https://changed.example.com")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.urlInput).hasValue(originalUrl)
        }

        "an HTTP monitor can be edited from its list row, and saving it leads back to the list" {
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "HTTP List Edit Source",
                url = "https://list-edit.example.com",
                uptimeCheckInterval = 120,
                category = "Payments",
            )
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "HTTP Neighbour",
                url = "https://neighbour.example.com",
            )

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("HTTP List Edit Source")
            assertThat(modal.title).hasText(Messages.updateMonitor("HTTP List Edit Source"))
            // Every value is loaded from the monitor of the row
            assertThat(modal.nameInput).hasValue("HTTP List Edit Source")
            assertThat(modal.nameInput).isEnabled()
            assertThat(modal.urlInput).hasValue("https://list-edit.example.com")
            assertThat(modal.uptimeCheckIntervalInput).hasValue("120")
            assertThat(modal.selectedCategory).hasText("Payments")

            modal.setName("HTTP List Edit Renamed")
                .setUrl("https://list-edited.example.com")
                .setCategory("")
                .save()

            // The list is reloaded in place, instead of navigating to the details page of the monitor
            assertThat(list.rowByName("HTTP List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/http-monitors"
            // ...and the monitor was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("HTTP List Edit Source")).hasCount(0)
            with(httpMonitorRepository.findByName("HTTP List Edit Renamed").shouldNotBeNull()) {
                url shouldBe "https://list-edited.example.com"
                category.shouldBeNull()
            }
            httpMonitorRepository.findByName("HTTP Neighbour").shouldNotBeNull().url shouldBe
                "https://neighbour.example.com"
        }

        "the list's modal loads the monitor of each row, discards abandoned edits and still creates new monitors" {
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "HTTP First Row",
                url = "https://first.example.com",
                category = "Payments",
            )
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "HTTP Second Row",
                url = "https://second.example.com",
            )

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val first = list.configureMonitor("HTTP First Row")
            assertThat(first.nameInput).hasValue("HTTP First Row")
            first.setName("HTTP First Row Abandoned").setUrl("https://abandoned.example.com").dismiss()

            val second = list.configureMonitor("HTTP Second Row")
            assertThat(second.title).hasText(Messages.updateMonitor("HTTP Second Row"))
            assertThat(second.nameInput).hasValue("HTTP Second Row")
            assertThat(second.urlInput).hasValue("https://second.example.com")
            assertThat(second.selectedCategory).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureMonitor("HTTP First Row")
            assertThat(firstAgain.nameInput).hasValue("HTTP First Row")
            assertThat(firstAgain.urlInput).hasValue("https://first.example.com")
            assertThat(firstAgain.selectedCategory).hasText("Payments")
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new monitor
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewHttpMonitor())
            assertThat(create.nameInput).hasValue("")
            assertThat(create.urlInput).hasValue("")
            assertThat(create.uptimeCheckIntervalInput).hasValue("60")
            assertThat(create.selectedCategory).hasCount(0)
            create.setName("HTTP Created After Edits").setUrl("https://created.example.com").save()
            page.waitForURL("**/http-monitors/*")
            assertThat(HttpMonitorDetailsPage(page).heading("HTTP Created After Edits")).isVisible()

            // None of the monitors opened on the way were changed
            httpMonitorRepository.findByName("HTTP First Row").shouldNotBeNull().url shouldBe
                "https://first.example.com"
            httpMonitorRepository.findByName("HTTP Second Row").shouldNotBeNull().url shouldBe
                "https://second.example.com"
        }

        "cloning and editing through the list's modal never mix up creating and updating an HTTP monitor" {
            createHttpMonitor(httpMonitorRepository, monitorName = "HTTP Edited", url = "https://edited.example.com")
            createHttpMonitor(httpMonitorRepository, monitorName = "HTTP Cloned", url = "https://cloned.example.com")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneMonitor("HTTP Cloned")
            assertThat(abandonedClone.nameInput).hasValue(Messages.clonedMonitorName("HTTP Cloned"))
            abandonedClone.dismiss()

            val edit = list.configureMonitor("HTTP Edited")
            assertThat(edit.nameInput).hasValue("HTTP Edited")
            edit.setName("HTTP Edited Renamed").save()
            assertThat(list.rowByName("HTTP Edited Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneMonitor("HTTP Cloned")
            assertThat(clone.title).hasText(Messages.createNewHttpMonitor())
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("HTTP Cloned"))
            assertThat(clone.urlInput).hasValue("https://cloned.example.com")
            clone.save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            assertThat(list.rowByName("HTTP Edited Renamed")).isVisible()
            assertThat(list.rowByName(Messages.clonedMonitorName("HTTP Cloned"))).isVisible()
            httpMonitorRepository.findByName("HTTP Cloned").shouldNotBeNull().url shouldBe "https://cloned.example.com"
            httpMonitorRepository.findByName("HTTP Edited").shouldBeNull()
        }

        "saving a monitor edited from the list keeps the category filter, and offers the categories added by it" {
            createHttpMonitor(httpMonitorRepository, monitorName = "card-payments", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val moved = list.configureMonitor("card-payments")
            assertThat(moved.selectedCategory).hasText("Payments")
            moved.setName("card-payments-moved").setCategory("Brand new").save()
            assertThat(list.rowByName("card-payments-moved")).isVisible()
            // The filter isn't part of the refreshed fragment, it's the reload that brings the new category in
            list.categoryOptions shouldContain "Brand new"

            list.navigateToCategory("Brand new")
            val renamed = list.configureMonitor("card-payments-moved")
            assertThat(renamed.nameInput).hasValue("card-payments-moved")
            renamed.setName("card-payments-renamed").save()
            assertThat(list.rowByName("card-payments-renamed")).isVisible()
            page.url() shouldContain "/http-monitors?category=Brand"
            list.selectedCategory shouldBe "Brand new"
            assertThat(list.rowByName("searches")).hasCount(0)
        }

        "the settings behind the accordions are loaded from the list row and survive the save" {
            createHttpMonitor(
                httpMonitorRepository,
                monitorName = "HTTP Accordion Source",
                url = "https://accordion.example.com",
                crossOriginHeaderPropagation = true,
                requestHeaders = mapOf("X-Api-Key" to "secret"),
            )

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("HTTP Accordion Source")
            assertThat(modal.nameInput).hasValue("HTTP Accordion Source")
            modal.expandRequestSettings()
            assertThat(modal.crossOriginHeaderPropagationToggle).isChecked()
            assertThat(modal.requestHeaderRow("X-Api-Key")).isVisible()

            modal.crossOriginHeaderPropagationToggle.uncheck()
            modal.setNewRequestHeader("X-Trace", "on").addRequestHeader()
            modal.setName("HTTP Accordion Saved").save()
            assertThat(list.rowByName("HTTP Accordion Saved")).isVisible()

            val reopened = list.configureMonitor("HTTP Accordion Saved")
            assertThat(reopened.nameInput).hasValue("HTTP Accordion Saved")
            reopened.expandRequestSettings()
            assertThat(reopened.crossOriginHeaderPropagationToggle).not().isChecked()
            assertThat(reopened.requestHeaderRow("X-Api-Key")).isVisible()
            assertThat(reopened.requestHeaderRow("X-Trace")).isVisible()
        }
    }
}
