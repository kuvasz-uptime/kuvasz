package com.kuvaszuptime.kuvasz.uitest.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DnsMonitorCrudUiTest(private val dnsMonitorRepository: DnsMonitorRepository) : UiTestSpec() {
    init {
        "a DNS monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: only name + host are required, the rest default to valid values.
            val originalName = "E2E DNS Monitor"
            list.openCreateModal().setName(originalName).setHost("example.com").setCategory("Payments").save()
            page.waitForURL("**/dns-monitors/*")
            val details = DnsMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            val updatedName = "E2E DNS Monitor Renamed"
            val configureModal = details.openConfigureModal()
            // The category is pre-filled from the monitor and can be cleared
            assertThat(configureModal.selectedCategory).hasText("Payments")
            configureModal.setName(updatedName).setCategory("").save()
            assertThat(details.heading(updatedName)).isVisible()
            assertThat(details.categoryBadge).hasCount(0)

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

        "a DNS monitor can be edited from its list row, and saving it leads back to the list" {
            createDnsMonitor(
                dnsMonitorRepository,
                monitorName = "DNS List Edit Source",
                host = "list-edit.example.com",
                resolverHost = "8.8.8.8",
                recordMatchers = listOf(DnsRecordMatcher(recordType = DnsRecordType.A, value = "1.2.3.4")),
                uptimeCheckInterval = 120,
                category = "Payments",
            )
            createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Neighbour", host = "neighbour.example.com")

            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("DNS List Edit Source")
            assertThat(modal.title).hasText(Messages.updateMonitor("DNS List Edit Source"))
            // Every value is loaded from the monitor of the row, including the record matchers behind the accordion
            assertThat(modal.nameInput).hasValue("DNS List Edit Source")
            assertThat(modal.nameInput).isEnabled()
            assertThat(modal.hostInput).hasValue("list-edit.example.com")
            assertThat(modal.resolverHostInput).hasValue("8.8.8.8")
            assertThat(modal.uptimeCheckIntervalInput).hasValue("120")
            assertThat(modal.selectedCategory).hasText("Payments")
            modal.openAssertionSettings()
            assertThat(modal.matcherRows).hasCount(1)
            assertThat(modal.matcherRow("1.2.3.4")).isVisible()

            modal.addMatcher("5.6.7.8")
            modal.setName("DNS List Edit Renamed").setResolverHost("1.1.1.1").setCategory("").save()

            // The list is reloaded in place, instead of navigating to the details page of the monitor
            assertThat(list.rowByName("DNS List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/dns-monitors"
            // ...and the monitor was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("DNS List Edit Source")).hasCount(0)
            with(dnsMonitorRepository.findByName("DNS List Edit Renamed").shouldNotBeNull()) {
                host shouldBe "list-edit.example.com"
                resolverHost shouldBe "1.1.1.1"
                category.shouldBeNull()
            }
            dnsMonitorRepository.findByName("DNS Neighbour").shouldNotBeNull().host shouldBe "neighbour.example.com"

            val reopened = list.configureMonitor("DNS List Edit Renamed")
            assertThat(reopened.nameInput).hasValue("DNS List Edit Renamed")
            reopened.openAssertionSettings()
            assertThat(reopened.matcherRows).hasCount(2)
            assertThat(reopened.matcherRow("5.6.7.8")).isVisible()
        }

        "the list's modal loads the monitor of each row, discards abandoned edits and still creates new monitors" {
            createDnsMonitor(
                dnsMonitorRepository,
                monitorName = "DNS First Row",
                host = "first.example.com",
                category = "Payments",
            )
            createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Second Row", host = "second.example.com")

            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            val first = list.configureMonitor("DNS First Row")
            assertThat(first.nameInput).hasValue("DNS First Row")
            first.setName("DNS First Row Abandoned").setHost("abandoned.example.com").dismiss()

            val second = list.configureMonitor("DNS Second Row")
            assertThat(second.title).hasText(Messages.updateMonitor("DNS Second Row"))
            assertThat(second.nameInput).hasValue("DNS Second Row")
            assertThat(second.hostInput).hasValue("second.example.com")
            assertThat(second.selectedCategory).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureMonitor("DNS First Row")
            assertThat(firstAgain.nameInput).hasValue("DNS First Row")
            assertThat(firstAgain.hostInput).hasValue("first.example.com")
            assertThat(firstAgain.selectedCategory).hasText("Payments")
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new monitor
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewDnsMonitor())
            assertThat(create.nameInput).hasValue("")
            assertThat(create.hostInput).hasValue("")
            assertThat(create.uptimeCheckIntervalInput).hasValue("60")
            assertThat(create.selectedCategory).hasCount(0)
            create.setName("DNS Created After Edits").setHost("created.example.com").save()
            page.waitForURL("**/dns-monitors/*")
            assertThat(DnsMonitorDetailsPage(page).heading("DNS Created After Edits")).isVisible()

            // None of the monitors opened on the way were changed
            dnsMonitorRepository.findByName("DNS First Row").shouldNotBeNull().host shouldBe "first.example.com"
            dnsMonitorRepository.findByName("DNS Second Row").shouldNotBeNull().host shouldBe "second.example.com"
        }

        "cloning and editing through the list's modal never mix up creating and updating a DNS monitor" {
            createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Edited", host = "edited.example.com")
            createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Cloned", host = "cloned.example.com")

            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneMonitor("DNS Cloned")
            assertThat(abandonedClone.nameInput).hasValue(Messages.clonedMonitorName("DNS Cloned"))
            abandonedClone.dismiss()

            val edit = list.configureMonitor("DNS Edited")
            assertThat(edit.nameInput).hasValue("DNS Edited")
            edit.setName("DNS Edited Renamed").save()
            assertThat(list.rowByName("DNS Edited Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneMonitor("DNS Cloned")
            assertThat(clone.title).hasText(Messages.createNewDnsMonitor())
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("DNS Cloned"))
            assertThat(clone.hostInput).hasValue("cloned.example.com")
            clone.save()
            page.waitForURL("**/dns-monitors/*")

            list.navigate()
            assertThat(list.rowByName("DNS Edited Renamed")).isVisible()
            assertThat(list.rowByName(Messages.clonedMonitorName("DNS Cloned"))).isVisible()
            dnsMonitorRepository.findByName("DNS Cloned").shouldNotBeNull().host shouldBe "cloned.example.com"
            dnsMonitorRepository.findByName("DNS Edited").shouldBeNull()
        }
    }
}
