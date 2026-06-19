package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.common.DetailsReadOnlyView
import com.kuvaszuptime.kuvasz.uitest.pages.common.ListReadOnlyView
import com.kuvaszuptime.kuvasz.uitest.pages.common.UpsertModalReadOnlyView
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Verifies that monitors and status pages configured via YAML are read-only in the UI: the app imports them at startup
 * and flips that type into read-only mode (no create/edit/delete)
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV, "ui-test-readonly"])
class ReadOnlyConfigUiTest : UiTestSpec() {

    override val resetDatabaseAfterEachTest = false

    init {
        "YAML-configured HTTP monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/http-monitors")
            list.navigate()
            assertListIsReadOnly(list, "yaml-http-monitor")

            val modal = openConfigModalFrom(page, list, "yaml-http-monitor")
            assertReadOnlyField(modal, "name", "yaml-http-monitor")
            assertReadOnlyField(modal, "url", "https://example.com")
            assertCannotBeSaved(modal)
        }

        "YAML-configured push monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/push-monitors")
            list.navigate()
            assertListIsReadOnly(list, "yaml-push-monitor")

            val modal = openConfigModalFrom(page, list, "yaml-push-monitor")
            assertReadOnlyField(modal, "name", "yaml-push-monitor")
            assertReadOnlyField(modal, "heartbeatInterval", "300")
            assertCannotBeSaved(modal)
        }

        "YAML-configured ICMP monitors are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/icmp-monitors")
            list.navigate()
            assertListIsReadOnly(list, "yaml-icmp-monitor")

            val modal = openConfigModalFrom(page, list, "yaml-icmp-monitor")
            assertReadOnlyField(modal, "name", "yaml-icmp-monitor")
            assertReadOnlyField(modal, "host", "127.0.0.1")
            assertCannotBeSaved(modal)
        }

        "YAML-configured status pages are read-only on the list, detail page and config modal" {
            val page = newPage()
            val list = ListReadOnlyView(page, "/status-pages")
            list.navigate()
            assertListIsReadOnly(list, "YAML Status Page")

            val modal = openConfigModalFrom(page, list, "YAML Status Page")
            assertReadOnlyField(modal, "title", "YAML Status Page")
            assertReadOnlyField(modal, "slug", "yaml-status-page")
            assertCannotBeSaved(modal)
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

    // Read-only badge present, no "add" button, and the row carries no toggle/delete actions.
    private fun assertListIsReadOnly(list: ListReadOnlyView, name: String) {
        assertThat(list.readOnlyBadge).isVisible()
        assertThat(list.addButton).hasCount(0)
        assertThat(list.row(name)).isVisible()
        assertThat(list.actionButtonsIn(name)).hasCount(0)
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
        assertThat(modal.closeButton).isVisible()
    }
}
