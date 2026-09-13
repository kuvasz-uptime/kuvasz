package com.kuvaszuptime.kuvasz.uitest.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowCrudUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a recurring maintenance window can be created and edited through the UI via an editable upsert modal" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val name = "E2E Maintenance Window"
            val modal = list.openCreateModal()
            modal.selectType(MaintenanceWindowType.CRON)
                .setName(name)
                .setCron("0 2 * * *")
                .blurCron()
            // Fill the duration through a quick-select preset rather than typing it.
            modal.durationPreset(Messages.hourInterval(1)).click()
            modal.save()

            page.waitForURL("**/maintenance-windows/*")
            val details = MaintenanceWindowDetailsPage(page)
            assertThat(details.heading(name)).isVisible()
            assertThat(details.configureButton).isVisible()

            // Contrast with the read-only case: the upsert modal here is editable, with Save available.
            val updatedName = "E2E Maintenance Window Renamed"
            val editModal = details.openConfigureModal()
            assertThat(editModal.nameInput).isEnabled()
            assertThat(editModal.saveButton).isVisible()
            editModal.setName(updatedName).save()
            assertThat(details.heading(updatedName)).isVisible()
        }

        "a manual maintenance window can be created through the UI" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val name = "E2E Manual Window"
            list.openCreateModal()
                .setName(name)
                .save()

            page.waitForURL("**/maintenance-windows/*")
            val details = MaintenanceWindowDetailsPage(page)
            assertThat(details.heading(name)).isVisible()
        }

        "a one-off maintenance window can be created through the UI" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val name = "E2E One-off Window"
            list.openCreateModal()
                .selectType(MaintenanceWindowType.SINGLE)
                .setName(name)
                .setStart("2030-01-01T10:00")
                .setDuration("PT1H")
                .save()

            page.waitForURL("**/maintenance-windows/*")
            val details = MaintenanceWindowDetailsPage(page)
            assertThat(details.heading(name)).isVisible()
        }

        "an abandoned create form is reset when the modal is reopened" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Abandoned Selection")

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
            modal.selectType(MaintenanceWindowType.CRON)
                .setName("Abandoned Window")
                .setCron("0 2 * * *")
                .setDuration("PT1H")
            modal.selectOption("Abandoned Selection")
            assertThat(modal.selectedOptions).hasCount(1)
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.cronInput).hasValue("")
            assertThat(reopened.durationInput).hasValue("")
            assertThat(reopened.selectedOptions).hasCount(0)
            // The schedule type falls back to the default a fresh form starts with.
            assertThat(reopened.typeRadio(MaintenanceWindowType.MANUAL)).isChecked()
        }

        "edits abandoned on an existing maintenance window are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val originalName = "Window Reset Source"
            val originalCron = "0 2 * * *"
            val modal = list.openCreateModal()
            modal.selectType(MaintenanceWindowType.CRON).setName(originalName).setCron(originalCron).blurCron()
            modal.durationPreset(Messages.hourInterval(1)).click()
            modal.save()
            page.waitForURL("**/maintenance-windows/*")
            val details = MaintenanceWindowDetailsPage(page)

            details.openConfigureModal()
                .setName("Window Reset Renamed")
                .setCron("30 4 * * *")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.cronInput).hasValue(originalCron)
            assertThat(reopened.typeRadio(MaintenanceWindowType.CRON)).isChecked()
        }

        "a window can be scoped to categories, which survive a reopen and show up on the details page" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Categorized", category = "Payments")

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val modal = list.openCreateModal().setName("Category Scoped Window")
            // The already used category is offered by the internal endpoint, a brand new one is created in place
            modal.offeredSelectableCategories shouldBe listOf("Payments")
            modal.selectCategory("Payments")
            modal.selectCategory("Brand new")
            assertThat(modal.selectedCategories).hasCount(2)
            modal.save()
            page.waitForURL("**/maintenance-windows/*")

            val details = MaintenanceWindowDetailsPage(page)
            assertThat(details.affectedCategories).hasCount(2)
            assertThat(details.affectedCategories).containsText(arrayOf("Brand new", "Payments"))

            val reopened = details.openConfigureModal()
            assertThat(reopened.selectedCategories).hasCount(2)
        }

        "the categories of a maintenance window can be cleared entirely" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val created = list.openCreateModal().setName("Cleared Categories")
            created.selectCategory("Payments")
            created.save()
            page.waitForURL("**/maintenance-windows/*")

            val details = MaintenanceWindowDetailsPage(page)
            val modal = details.openConfigureModal()
            assertThat(modal.selectedCategories).hasCount(1)
            modal.clearCategories()
            modal.save()

            // The empty array has to reach the server, not be treated as "leave it alone"
            assertThat(details.affectedCategories).hasCount(0)
            val reopened = details.openConfigureModal()
            assertThat(reopened.selectedCategories).hasCount(0)
        }

        "the categories of an abandoned maintenance window form are reset when the modal is reopened" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val modal = list.openCreateModal().setName("Abandoned Categories")
            modal.selectCategory("Payments")
            assertThat(modal.selectedCategories).hasCount(1)
            modal.dismiss()

            val reopened = list.openCreateModal()
            assertThat(reopened.selectedCategories).hasCount(0)
        }

        "the modal's monitor multi-select adds a seeded monitor" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Selectable Monitor")

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("Selectable Monitor")
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Selectable Monitor")
        }
    }
}
