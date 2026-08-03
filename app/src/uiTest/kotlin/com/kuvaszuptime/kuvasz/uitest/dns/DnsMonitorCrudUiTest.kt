package com.kuvaszuptime.kuvasz.uitest.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DnsMonitorCrudUiTest : UiTestSpec() {
    init {
        "a DNS monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: only name + host are required, the rest default to valid values.
            val originalName = "E2E DNS Monitor"
            list.openCreateModal().setName(originalName).setHost("example.com").save()
            page.waitForURL("**/dns-monitors/*")
            val details = DnsMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()

            val updatedName = "E2E DNS Monitor Renamed"
            details.openConfigureModal().setName(updatedName).save()
            assertThat(details.heading(updatedName)).isVisible()

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }

        "a DNS monitor can be created with a record matcher added through the repeating-row editor" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val name = "DNS Matcher Monitor"
            val modal = list.openCreateModal().setName(name).setHost("example.com")
            modal.addMatcher("1.2.3.4")
            assertThat(modal.matcherRows).hasCount(1)
            assertThat(modal.matcherRows).containsText("1.2.3.4")
            modal.save()
            page.waitForURL("**/dns-monitors/*")

            list.navigate()
            assertThat(list.rowByName(name)).isVisible()
        }

        "enabling drift detection reveals the watched record types, which survive the save" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val name = "DNS Drift Monitor"
            val modal = list.openCreateModal().setName(name).setHost("example.com")

            // The record-type checkboxes live behind an Alpine `x-if` on the drift toggle.
            modal.openAssertionSettings()
            assertThat(modal.driftRecordTypeCheckbox("NS")).hasCount(0)

            modal.enableDriftDetection()
            assertThat(modal.driftRecordTypeCheckbox("NS")).isVisible()
            modal.driftRecordTypeCheckbox("NS").check()
            modal.save()
            page.waitForURL("**/dns-monitors/*")

            // Re-opening the monitor's configuration must show the persisted drift settings.
            val details = DnsMonitorDetailsPage(page)
            val reopened = details.openConfigureModal()
            assertThat(reopened.driftDetectionToggle).isChecked()
            assertThat(reopened.driftRecordTypeCheckbox("NS")).isChecked()
            assertThat(reopened.driftRecordTypeCheckbox("MX")).not().isChecked()
        }

        "a DNS monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val sourceName = "DNS Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setHost("example.com")
                .setResolverHost("8.8.8.8")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/dns-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.hostInput).hasValue("example.com")
            // The DNS-specific custom resolver round-trips through create -> clone.
            assertThat(cloneModal.resolverHostInput).hasValue("8.8.8.8")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/dns-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned DNS Monitor")
                .setHost("abandoned.example.com")
                .setResolverHost("8.8.8.8")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.hostInput).hasValue("")
            assertThat(reopened.resolverHostInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val originalName = "DNS Reset Source"
            val originalHost = "original.example.com"
            list.openCreateModal().setName(originalName).setHost(originalHost).setResolverHost("1.1.1.1").save()
            page.waitForURL("**/dns-monitors/*")
            val details = DnsMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("DNS Reset Renamed")
                .setHost("changed.example.com")
                .setResolverHost("8.8.8.8")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.hostInput).hasValue(originalHost)
            assertThat(reopened.resolverHostInput).hasValue("1.1.1.1")
        }
    }
}
