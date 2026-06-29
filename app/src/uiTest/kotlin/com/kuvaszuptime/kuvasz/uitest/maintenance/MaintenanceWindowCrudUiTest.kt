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
