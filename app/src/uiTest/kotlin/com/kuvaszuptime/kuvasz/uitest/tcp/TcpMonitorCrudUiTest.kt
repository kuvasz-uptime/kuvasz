package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class TcpMonitorCrudUiTest(private val tcpMonitorRepository: TcpMonitorRepository) : UiTestSpec() {
    init {
        "a TCP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: name + host + port are required, the rest default to valid values.
            val originalName = "E2E TCP Monitor"
            list.openCreateModal()
                .setName(originalName)
                .setHost("127.0.0.1")
                .setPort("5432")
                .setCategory("Payments")
                .save()
            page.waitForURL("**/tcp-monitors/*")
            val details = TcpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            val updatedName = "E2E TCP Monitor Renamed"
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

        "a TCP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val sourceName = "TCP Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setHost("127.0.0.1")
                .setPort("8080")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/tcp-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.hostInput).hasValue("127.0.0.1")
            assertThat(cloneModal.portInput).hasValue("8080")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/tcp-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned TCP Monitor")
                .setHost("abandoned.example.com")
                .setPort("9999")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.hostInput).hasValue("")
            assertThat(reopened.portInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val originalName = "TCP Reset Source"
            val originalHost = "original.example.com"
            list.openCreateModal().setName(originalName).setHost(originalHost).setPort("5432").save()
            page.waitForURL("**/tcp-monitors/*")
            val details = TcpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("TCP Reset Renamed")
                .setHost("changed.example.com")
                .setPort("9999")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.hostInput).hasValue(originalHost)
            assertThat(reopened.portInput).hasValue("5432")
        }

        "a TCP monitor can be edited from its list row, and saving it leads back to the list" {
            createTcpMonitor(
                tcpMonitorRepository,
                monitorName = "TCP List Edit Source",
                host = "list-edit.example.com",
                port = 5432,
                uptimeCheckInterval = 120,
                category = "Payments",
            )
            createTcpMonitor(tcpMonitorRepository, monitorName = "TCP Neighbour", host = "neighbour.example.com")

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("TCP List Edit Source")
            assertThat(modal.title).hasText(Messages.updateMonitor("TCP List Edit Source"))
            // Every value is loaded from the monitor of the row
            assertThat(modal.nameInput).hasValue("TCP List Edit Source")
            assertThat(modal.nameInput).isEnabled()
            assertThat(modal.hostInput).hasValue("list-edit.example.com")
            assertThat(modal.portInput).hasValue("5432")
            assertThat(modal.uptimeCheckIntervalInput).hasValue("120")
            assertThat(modal.selectedCategory).hasText("Payments")

            modal.setName("TCP List Edit Renamed")
                .setHost("list-edited.example.com")
                .setPort("6543")
                .setCategory("")
                .save()

            // The list is reloaded in place, instead of navigating to the details page of the monitor
            assertThat(list.rowByName("TCP List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/tcp-monitors"
            // ...and the monitor was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("TCP List Edit Source")).hasCount(0)
            with(tcpMonitorRepository.findByName("TCP List Edit Renamed").shouldNotBeNull()) {
                host shouldBe "list-edited.example.com"
                port.toString() shouldBe "6543"
                category.shouldBeNull()
            }
            tcpMonitorRepository.findByName("TCP Neighbour").shouldNotBeNull().host shouldBe "neighbour.example.com"
        }

        "the list's modal loads the monitor of each row, discards abandoned edits and still creates new monitors" {
            createTcpMonitor(
                tcpMonitorRepository,
                monitorName = "TCP First Row",
                host = "first.example.com",
                port = 5432,
                category = "Payments",
            )
            createTcpMonitor(
                tcpMonitorRepository,
                monitorName = "TCP Second Row",
                host = "second.example.com",
                port = 6379,
            )

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val first = list.configureMonitor("TCP First Row")
            assertThat(first.nameInput).hasValue("TCP First Row")
            first.setName("TCP First Row Abandoned").setPort("9999").dismiss()

            val second = list.configureMonitor("TCP Second Row")
            assertThat(second.title).hasText(Messages.updateMonitor("TCP Second Row"))
            assertThat(second.nameInput).hasValue("TCP Second Row")
            assertThat(second.hostInput).hasValue("second.example.com")
            assertThat(second.portInput).hasValue("6379")
            assertThat(second.selectedCategory).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureMonitor("TCP First Row")
            assertThat(firstAgain.nameInput).hasValue("TCP First Row")
            assertThat(firstAgain.portInput).hasValue("5432")
            assertThat(firstAgain.selectedCategory).hasText("Payments")
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new monitor
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewTcpMonitor())
            assertThat(create.nameInput).hasValue("")
            assertThat(create.hostInput).hasValue("")
            assertThat(create.portInput).hasValue("")
            assertThat(create.uptimeCheckIntervalInput).hasValue("60")
            assertThat(create.selectedCategory).hasCount(0)
            create.setName("TCP Created After Edits").setHost("created.example.com").setPort("8443").save()
            page.waitForURL("**/tcp-monitors/*")
            assertThat(TcpMonitorDetailsPage(page).heading("TCP Created After Edits")).isVisible()

            // None of the monitors opened on the way were changed
            tcpMonitorRepository.findByName("TCP First Row").shouldNotBeNull().port.toString() shouldBe "5432"
            tcpMonitorRepository.findByName("TCP Second Row").shouldNotBeNull().port.toString() shouldBe "6379"
        }

        "cloning and editing through the list's modal never mix up creating and updating a TCP monitor" {
            createTcpMonitor(tcpMonitorRepository, monitorName = "TCP Edited", host = "edited.example.com")
            createTcpMonitor(tcpMonitorRepository, monitorName = "TCP Cloned", host = "cloned.example.com", port = 6379)

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneMonitor("TCP Cloned")
            assertThat(abandonedClone.nameInput).hasValue(Messages.clonedMonitorName("TCP Cloned"))
            abandonedClone.dismiss()

            val edit = list.configureMonitor("TCP Edited")
            assertThat(edit.nameInput).hasValue("TCP Edited")
            edit.setName("TCP Edited Renamed").save()
            assertThat(list.rowByName("TCP Edited Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneMonitor("TCP Cloned")
            assertThat(clone.title).hasText(Messages.createNewTcpMonitor())
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("TCP Cloned"))
            assertThat(clone.portInput).hasValue("6379")
            clone.save()
            page.waitForURL("**/tcp-monitors/*")

            list.navigate()
            assertThat(list.rowByName("TCP Edited Renamed")).isVisible()
            assertThat(list.rowByName(Messages.clonedMonitorName("TCP Cloned"))).isVisible()
            tcpMonitorRepository.findByName("TCP Cloned").shouldNotBeNull().host shouldBe "cloned.example.com"
            tcpMonitorRepository.findByName("TCP Edited").shouldBeNull()
        }
    }
}
