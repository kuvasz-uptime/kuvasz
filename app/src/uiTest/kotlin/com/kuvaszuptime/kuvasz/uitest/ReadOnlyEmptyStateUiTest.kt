package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * A YAML config declaring an explicitly empty list still flips its type into read-only mode, which leaves the list
 * read-only *and* empty. Its placeholder can't offer the "create" button the editable one points at, so it explains
 * where the given resource can be created instead.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV, "ui-test-readonly-empty"])
class ReadOnlyEmptyStateUiTest : UiTestSpec() {

    override val resetDatabaseAfterEachTest = false

    init {
        "the empty monitor list of a YAML-managed type points at the configuration file" {
            val page = newPage()
            val list = HttpMonitorListPage(page)

            list.navigate()

            assertThat(list.emptyState).containsText(Messages.noMonitorsYet())
            assertThat(list.emptyState).containsText(Messages.noMonitorsReadOnlyDescription())
            assertThat(list.emptyState).not().containsText(Messages.noMonitorsDescription())
        }

        "the empty maintenance window list of a YAML-managed setup points at the configuration file" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)

            list.navigate()

            assertThat(list.emptyState).containsText(Messages.noMaintenanceWindowsYet())
            assertThat(list.emptyState).containsText(Messages.noMaintenanceWindowsReadOnlyDescription())
            assertThat(list.emptyState).not().containsText(Messages.noMaintenanceWindowsDescription())
        }
    }
}
