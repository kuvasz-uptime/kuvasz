package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorCrudUiTest(private val icmpMonitorRepository: IcmpMonitorRepository) : UiTestSpec() {
    init {
        "an ICMP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: name + host are required, the rest default to valid values.
            val originalName = "E2E ICMP Monitor"
            list.openCreateModal().setName(originalName).setHost("127.0.0.1").setCategory("Payments").save()
            page.waitForURL("**/icmp-monitors/*")
            val details = IcmpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            val updatedName = "E2E ICMP Monitor Renamed"
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

        "an ICMP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val sourceName = "ICMP Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setHost("127.0.0.1")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/icmp-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.hostInput).hasValue("127.0.0.1")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/icmp-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned ICMP Monitor")
                .setHost("abandoned.example.com")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.hostInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val originalName = "ICMP Reset Source"
            val originalHost = "original.example.com"
            list.openCreateModal().setName(originalName).setHost(originalHost).save()
            page.waitForURL("**/icmp-monitors/*")
            val details = IcmpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("ICMP Reset Renamed")
                .setHost("changed.example.com")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.hostInput).hasValue(originalHost)
        }

        "an ICMP monitor can be edited from its list row, and saving it leads back to the list" {
            createIcmpMonitor(
                icmpMonitorRepository,
                monitorName = "ICMP List Edit Source",
                host = "list-edit.example.com",
                uptimeCheckInterval = 120,
                category = "Payments",
            )
            createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Neighbour", host = "neighbour.example.com")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("ICMP List Edit Source")
            assertThat(modal.title).hasText(Messages.updateMonitor("ICMP List Edit Source"))
            // Every value is loaded from the monitor of the row
            assertThat(modal.nameInput).hasValue("ICMP List Edit Source")
            assertThat(modal.nameInput).isEnabled()
            assertThat(modal.hostInput).hasValue("list-edit.example.com")
            assertThat(modal.uptimeCheckIntervalInput).hasValue("120")
            assertThat(modal.selectedCategory).hasText("Payments")

            modal.setName("ICMP List Edit Renamed").setHost("list-edited.example.com").setCategory("").save()

            // The list is reloaded in place, instead of navigating to the details page of the monitor
            assertThat(list.rowByName("ICMP List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/icmp-monitors"
            // ...and the monitor was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("ICMP List Edit Source")).hasCount(0)
            with(icmpMonitorRepository.findByName("ICMP List Edit Renamed").shouldNotBeNull()) {
                host shouldBe "list-edited.example.com"
                category.shouldBeNull()
            }
            icmpMonitorRepository.findByName("ICMP Neighbour").shouldNotBeNull().host shouldBe "neighbour.example.com"
        }

        "the list's modal loads the monitor of each row, discards abandoned edits and still creates new monitors" {
            createIcmpMonitor(
                icmpMonitorRepository,
                monitorName = "ICMP First Row",
                host = "first.example.com",
                category = "Payments",
            )
            createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Second Row", host = "second.example.com")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val first = list.configureMonitor("ICMP First Row")
            assertThat(first.nameInput).hasValue("ICMP First Row")
            first.setName("ICMP First Row Abandoned").setHost("abandoned.example.com").dismiss()

            val second = list.configureMonitor("ICMP Second Row")
            assertThat(second.title).hasText(Messages.updateMonitor("ICMP Second Row"))
            assertThat(second.nameInput).hasValue("ICMP Second Row")
            assertThat(second.hostInput).hasValue("second.example.com")
            assertThat(second.selectedCategory).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureMonitor("ICMP First Row")
            assertThat(firstAgain.nameInput).hasValue("ICMP First Row")
            assertThat(firstAgain.hostInput).hasValue("first.example.com")
            assertThat(firstAgain.selectedCategory).hasText("Payments")
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new monitor
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewIcmpMonitor())
            assertThat(create.nameInput).hasValue("")
            assertThat(create.hostInput).hasValue("")
            assertThat(create.uptimeCheckIntervalInput).hasValue("60")
            assertThat(create.selectedCategory).hasCount(0)
            create.setName("ICMP Created After Edits").setHost("created.example.com").save()
            page.waitForURL("**/icmp-monitors/*")
            assertThat(IcmpMonitorDetailsPage(page).heading("ICMP Created After Edits")).isVisible()

            // None of the monitors opened on the way were changed
            icmpMonitorRepository.findByName("ICMP First Row").shouldNotBeNull().host shouldBe "first.example.com"
            icmpMonitorRepository.findByName("ICMP Second Row").shouldNotBeNull().host shouldBe "second.example.com"
        }

        "cloning and editing through the list's modal never mix up creating and updating an ICMP monitor" {
            createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Edited", host = "edited.example.com")
            createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Cloned", host = "cloned.example.com")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneMonitor("ICMP Cloned")
            assertThat(abandonedClone.nameInput).hasValue(Messages.clonedMonitorName("ICMP Cloned"))
            abandonedClone.dismiss()

            val edit = list.configureMonitor("ICMP Edited")
            assertThat(edit.nameInput).hasValue("ICMP Edited")
            edit.setName("ICMP Edited Renamed").save()
            assertThat(list.rowByName("ICMP Edited Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneMonitor("ICMP Cloned")
            assertThat(clone.title).hasText(Messages.createNewIcmpMonitor())
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("ICMP Cloned"))
            assertThat(clone.hostInput).hasValue("cloned.example.com")
            clone.save()
            page.waitForURL("**/icmp-monitors/*")

            list.navigate()
            assertThat(list.rowByName("ICMP Edited Renamed")).isVisible()
            assertThat(list.rowByName(Messages.clonedMonitorName("ICMP Cloned"))).isVisible()
            icmpMonitorRepository.findByName("ICMP Cloned").shouldNotBeNull().host shouldBe "cloned.example.com"
            icmpMonitorRepository.findByName("ICMP Edited").shouldBeNull()
        }
    }
}
