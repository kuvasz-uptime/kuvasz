package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.common.DetailsReadOnlyView
import com.kuvaszuptime.kuvasz.uitest.pages.common.ListReadOnlyView
import com.kuvaszuptime.kuvasz.uitest.pages.common.UpsertModalReadOnlyView
import com.kuvaszuptime.kuvasz.uitest.pages.settings.SettingsBackupPage
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.regex.Pattern

/**
 * Verifies that monitors, status pages and maintenance windows configured via YAML are read-only in the UI: the app
 * imports them at startup and flips that type into read-only mode (no create/edit/delete)
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV, "ui-test-readonly"])
class ReadOnlyConfigUiTest : UiTestSpec() {

    override val resetDatabaseAfterEachTest = false

    init {
        "YAML-configured HTTP monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/http-monitors")
            list.navigate()
            assertMonitorListIsReadOnly(list, "yaml-http-monitor")

            // The monitor is on a read-only status page as well, which must not make any difference in read-only mode
            assertMonitorConfigIsReadOnly(
                page,
                list,
                "yaml-http-monitor",
                "name" to "yaml-http-monitor",
                "url" to "https://example.com",
            )
        }

        "YAML-configured push monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/push-monitors")
            list.navigate()
            assertMonitorListIsReadOnly(list, "yaml-push-monitor")

            assertMonitorConfigIsReadOnly(
                page,
                list,
                "yaml-push-monitor",
                "name" to "yaml-push-monitor",
                "heartbeatInterval" to "300",
            )
        }

        "YAML-configured ICMP monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/icmp-monitors")
            list.navigate()
            assertMonitorListIsReadOnly(list, "yaml-icmp-monitor")

            assertMonitorConfigIsReadOnly(
                page,
                list,
                "yaml-icmp-monitor",
                "name" to "yaml-icmp-monitor",
                "host" to "127.0.0.1",
            )
        }

        "YAML-configured TCP monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/tcp-monitors")
            list.navigate()
            assertMonitorListIsReadOnly(list, "yaml-tcp-monitor")

            assertMonitorConfigIsReadOnly(
                page,
                list,
                "yaml-tcp-monitor",
                "name" to "yaml-tcp-monitor",
                "host" to "127.0.0.1",
                "port" to "8080",
            )
        }

        "YAML-configured DNS monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/dns-monitors")
            list.navigate()
            assertMonitorListIsReadOnly(list, "yaml-dns-monitor")

            assertMonitorConfigIsReadOnly(
                page,
                list,
                "yaml-dns-monitor",
                "name" to "yaml-dns-monitor",
                "host" to "example.com",
                "resolverPort" to "53",
            )
        }

        "YAML-configured status pages are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/status-pages")
            list.navigate()
            assertListIsReadOnly(list, "YAML Status Page")
            assertThat(list.actionButtonsIn("YAML Status Page")).hasCount(0)

            val modal = openConfigModalFrom(page, list, "YAML Status Page")
            assertReadOnlyField(modal, "title", "YAML Status Page")
            assertReadOnlyField(modal, "slug", "yaml-status-page")
            assertCannotBeSaved(modal)
        }

        "YAML-configured maintenance windows are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/maintenance-windows")
            list.navigate()
            assertListIsReadOnly(list, "yaml-maintenance-window")
            assertThat(list.actionButtonsIn("yaml-maintenance-window")).hasCount(0)

            val modal = openConfigModalFrom(page, list, "yaml-maintenance-window")
            assertReadOnlyField(modal, "name", "yaml-maintenance-window")
            assertReadOnlyField(modal, "cron", "0 2 * * *")
            assertReadOnlyField(modal, "duration", "PT1H")
            assertCannotBeSaved(modal)
        }

        "the backup dropdown disables the status page and maintenance window import items when read-only" {
            val page = newPage()
            val settings = SettingsBackupPage(page)
            settings.navigate()
            settings.openBackupMenu()

            val disabled = Pattern.compile(".*\\bdisabled\\b.*")
            assertThat(settings.importStatusPagesItem).hasClass(disabled)
            assertThat(settings.importStatusPagesItem.getByTestId("read-only-badge")).isVisible()
            assertThat(settings.importMaintenanceWindowsItem).hasClass(disabled)
            assertThat(settings.importMaintenanceWindowsItem.getByTestId("read-only-badge")).isVisible()
            assertThat(settings.importMonitorsItem).hasClass(disabled)
            assertThat(settings.importMonitorsItem.getByTestId("read-only-badge")).isVisible()
        }
    }

    // Opens the entity's detail page, checks the read-only header, then opens the view-only "Configuration" modal.
    private fun openConfigModalFrom(page: Page, list: ListReadOnlyView, name: String): UpsertModalReadOnlyView {
        list.openDetails(name)
        val details = DetailsReadOnlyView(page)
        assertThat(details.configurationButton).isVisible()
        assertThat(details.configureButton).hasCount(0)
        return details.openConfigurationModal()
    }

    /**
     * The configuration of a monitor is shown read-only both from its row on the list, without leaving it, and from its
     * details page, and the two show exactly the same [fields].
     */
    private fun assertMonitorConfigIsReadOnly(
        page: Page,
        list: ListReadOnlyView,
        name: String,
        vararg fields: Pair<String, String>,
    ) {
        val fromList = list.openConfigurationModal(name)
        assertMonitorModalIsReadOnly(fromList, name, fields)
        fromList.dismiss()
        page.url() shouldEndWith list.listPath
        assertThat(list.row(name)).isVisible()

        assertMonitorModalIsReadOnly(openConfigModalFrom(page, list, name), name, fields)
    }

    private fun assertMonitorModalIsReadOnly(
        modal: UpsertModalReadOnlyView,
        name: String,
        fields: Array<out Pair<String, String>>,
    ) {
        assertThat(modal.title).hasText(Messages.configurationOf(name))
        fields.forEach { (propName, expectedValue) -> assertReadOnlyField(modal, propName, expectedValue) }
        assertCannotBeSaved(modal)
    }

    // Read-only badge present, no "add" button, and the row is listed.
    private fun assertListIsReadOnly(list: ListReadOnlyView, name: String) {
        assertThat(list.readOnlyBadge).isVisible()
        assertThat(list.addButton).hasCount(0)
        assertThat(list.row(name)).isVisible()
    }

    // A read-only monitor's row carries no toggle/clone/delete actions, only the view-only configuration button.
    private fun assertMonitorListIsReadOnly(list: ListReadOnlyView, name: String) {
        assertListIsReadOnly(list, name)
        assertThat(list.actionButtonsIn(name)).hasCount(1)
        assertThat(list.configurationButtonIn(name)).isVisible()
    }

    private fun assertReadOnlyField(modal: UpsertModalReadOnlyView, propName: String, expectedValue: String) {
        with(assertThat(modal.field(propName))) {
            hasValue(expectedValue)
            isDisabled()
        }
    }

    // Saving is not possible — only "Close" is offered.
    private fun assertCannotBeSaved(modal: UpsertModalReadOnlyView) {
        assertThat(modal.saveButton).hasCount(0)
        assertThat(modal.dismissButton).isVisible()
    }
}
