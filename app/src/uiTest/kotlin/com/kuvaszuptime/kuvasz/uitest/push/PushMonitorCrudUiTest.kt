package com.kuvaszuptime.kuvasz.uitest.push

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PushMonitorCrudUiTest(private val pushMonitorRepository: PushMonitorRepository) : UiTestSpec() {
    init {
        "a push monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: only the name is required — the client secret is auto-generated and the interval defaults to 10.
            val originalName = "E2E Push Monitor"
            list.openCreateModal().setName(originalName).setCategory("Payments").save()
            page.waitForURL("**/push-monitors/*")
            val details = PushMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            val updatedName = "E2E Push Monitor Renamed"
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

        "a push monitor can be cloned from the list, regenerating its client secret" {
            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            val sourceName = "Push Clone Source"
            list.openCreateModal().setName(sourceName).setHeartbeatInterval("30").save()
            page.waitForURL("**/push-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.heartbeatIntervalInput).hasValue("30")
            // A fresh client secret is generated for the clone (the source's is unique).
            assertThat(cloneModal.clientSecretInput).not().hasValue("")

            // Saving succeeds only because the unique client secret was regenerated.
            cloneModal.save()
            page.waitForURL("**/push-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned Push Monitor")
                .setHeartbeatInterval("300")
                .setClientSecret("abandoned-client-secret")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.heartbeatIntervalInput).hasValue("10")
            // The secret is auto-generated on every reset, so the abandoned one is gone rather than merely cleared.
            assertThat(reopened.clientSecretInput).not().hasValue("abandoned-client-secret")
            assertThat(reopened.clientSecretInput).not().hasValue("")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            val originalName = "Push Reset Source"
            list.openCreateModal().setName(originalName).setHeartbeatInterval("30").save()
            page.waitForURL("**/push-monitors/*")
            val details = PushMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("Push Reset Renamed")
                .setHeartbeatInterval("600")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.heartbeatIntervalInput).hasValue("30")
        }

        "a push monitor edited from its list row keeps its client secret, and saving it leads back to the list" {
            val clientSecret = "push-list-edit-secret-0123456789abcdef"
            createPushMonitor(
                pushMonitorRepository,
                monitorName = "Push List Edit Source",
                clientSecret = clientSecret,
                heartbeatInterval = 30,
                category = "Payments",
            )
            createPushMonitor(pushMonitorRepository, monitorName = "Push Neighbour", heartbeatInterval = 300)

            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor("Push List Edit Source")
            assertThat(modal.title).hasText(Messages.updateMonitor("Push List Edit Source"))
            // Every value is loaded from the monitor of the row, including its own client secret
            assertThat(modal.nameInput).hasValue("Push List Edit Source")
            assertThat(modal.nameInput).isEnabled()
            assertThat(modal.heartbeatIntervalInput).hasValue("30")
            assertThat(modal.clientSecretInput).hasValue(clientSecret)
            assertThat(modal.selectedCategory).hasText("Payments")

            modal.setName("Push List Edit Renamed").setHeartbeatInterval("60").setCategory("").save()

            // The list is reloaded in place, instead of navigating to the details page of the monitor
            assertThat(list.rowByName("Push List Edit Renamed")).isVisible()
            page.url() shouldEndWith "/push-monitors"
            // ...and the monitor was updated rather than created anew, while its neighbour was left alone
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("Push List Edit Source")).hasCount(0)
            with(pushMonitorRepository.findByName("Push List Edit Renamed").shouldNotBeNull()) {
                this.clientSecret shouldBe clientSecret
                heartbeatInterval.toString() shouldBe "60"
                category.shouldBeNull()
            }
            pushMonitorRepository.findByName("Push Neighbour").shouldNotBeNull().heartbeatInterval.toString() shouldBe
                "300"

            // Cloning the very same row gives the clone a secret of its own
            val clone = list.cloneMonitor("Push List Edit Renamed")
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("Push List Edit Renamed"))
            assertThat(clone.clientSecretInput).not().hasValue(clientSecret)
            assertThat(clone.clientSecretInput).not().hasValue("")
        }

        "the list's modal loads the monitor of each row, discards abandoned edits and still creates new monitors" {
            createPushMonitor(
                pushMonitorRepository,
                monitorName = "Push First Row",
                heartbeatInterval = 30,
                category = "Payments",
            )
            createPushMonitor(pushMonitorRepository, monitorName = "Push Second Row", heartbeatInterval = 90)

            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            val first = list.configureMonitor("Push First Row")
            assertThat(first.nameInput).hasValue("Push First Row")
            first.setName("Push First Row Abandoned").setHeartbeatInterval("600").dismiss()

            val second = list.configureMonitor("Push Second Row")
            assertThat(second.title).hasText(Messages.updateMonitor("Push Second Row"))
            assertThat(second.nameInput).hasValue("Push Second Row")
            assertThat(second.heartbeatIntervalInput).hasValue("90")
            assertThat(second.selectedCategory).hasCount(0)
            second.dismiss()

            val firstAgain = list.configureMonitor("Push First Row")
            assertThat(firstAgain.nameInput).hasValue("Push First Row")
            assertThat(firstAgain.heartbeatIntervalInput).hasValue("30")
            assertThat(firstAgain.selectedCategory).hasText("Payments")
            firstAgain.dismiss()

            // The header button still opens a blank create form, which creates a brand new monitor
            val create = list.openCreateModal()
            assertThat(create.title).hasText(Messages.createNewPushMonitor())
            assertThat(create.nameInput).hasValue("")
            assertThat(create.heartbeatIntervalInput).hasValue("10")
            assertThat(create.selectedCategory).hasCount(0)
            create.setName("Push Created After Edits").save()
            page.waitForURL("**/push-monitors/*")
            assertThat(PushMonitorDetailsPage(page).heading("Push Created After Edits")).isVisible()

            // None of the monitors opened on the way were changed
            pushMonitorRepository.findByName("Push First Row").shouldNotBeNull().heartbeatInterval.toString() shouldBe
                "30"
            pushMonitorRepository.findByName("Push Second Row").shouldNotBeNull().heartbeatInterval.toString() shouldBe
                "90"
        }

        "cloning and editing through the list's modal never mix up creating and updating a push monitor" {
            createPushMonitor(pushMonitorRepository, monitorName = "Push Edited", heartbeatInterval = 30)
            createPushMonitor(pushMonitorRepository, monitorName = "Push Cloned", heartbeatInterval = 90)

            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()

            // An abandoned clone doesn't turn the next edit into a create...
            val abandonedClone = list.cloneMonitor("Push Cloned")
            assertThat(abandonedClone.nameInput).hasValue(Messages.clonedMonitorName("Push Cloned"))
            abandonedClone.dismiss()

            val edit = list.configureMonitor("Push Edited")
            assertThat(edit.nameInput).hasValue("Push Edited")
            edit.setName("Push Edited Renamed").save()
            assertThat(list.rowByName("Push Edited Renamed")).isVisible()
            assertThat(list.rows).hasCount(2)

            // ...and an edit doesn't turn the next clone into an update
            val clone = list.cloneMonitor("Push Cloned")
            assertThat(clone.title).hasText(Messages.createNewPushMonitor())
            assertThat(clone.nameInput).hasValue(Messages.clonedMonitorName("Push Cloned"))
            assertThat(clone.heartbeatIntervalInput).hasValue("90")
            clone.save()
            page.waitForURL("**/push-monitors/*")

            list.navigate()
            assertThat(list.rowByName("Push Edited Renamed")).isVisible()
            assertThat(list.rowByName(Messages.clonedMonitorName("Push Cloned"))).isVisible()
            pushMonitorRepository.findByName("Push Cloned").shouldNotBeNull().heartbeatInterval.toString() shouldBe "90"
            pushMonitorRepository.findByName("Push Edited").shouldBeNull()
        }
    }
}
