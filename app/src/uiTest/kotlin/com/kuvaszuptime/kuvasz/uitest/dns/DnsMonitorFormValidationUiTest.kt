package com.kuvaszuptime.kuvasz.uitest.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldBeNull
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Exercises the Alpine.js validation in the DNS monitor create modal — the field-level checks, the server-side
 * conflict (duplicate name), and the complex record-matchers component.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DnsMonitorFormValidationUiTest(private val dnsMonitorRepository: DnsMonitorRepository) : UiTestSpec() {
    init {
        "a missing host is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setName("DNS Validation").save()
            modal shouldRejectWith Messages.errorHostRequired()

            modal.setHost("example.com")
            modal shouldAcceptAfterFixing Messages.errorHostRequired()
        }

        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setHost("example.com").save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range resolver port is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("DNS Port").setHost("example.com").setResolverPort("70000")
            modal shouldRejectWith Messages.errorDnsResolverPortInvalid()

            modal.setResolverPort("53")
            modal shouldAcceptAfterFixing Messages.errorDnsResolverPortInvalid()
        }

        "an invalid latency threshold is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("DNS Latency").setHost("example.com").setLatencyThreshold("0")
            modal shouldRejectWith Messages.errorLatencyThresholdInvalid()

            modal.setLatencyThreshold("500")
            modal shouldAcceptAfterFixing Messages.errorLatencyThresholdInvalid()
        }

        "an out-of-range uptime check interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("DNS Interval").setHost("example.com").setUptimeCheckInterval("1")
            modal shouldRejectWith Messages.errorUptimeCheckIntervalInvalid()

            modal.setUptimeCheckInterval("60")
            modal shouldAcceptAfterFixing Messages.errorUptimeCheckIntervalInvalid()
        }

        "creating a monitor with an already-used name surfaces the server-side conflict on the form" {
            createDnsMonitor(dnsMonitorRepository, monitorName = "Existing DNS Monitor")
            val modal = openCreateModal()

            modal.setName("Existing DNS Monitor").setHost("example.com").save()

            // The POST returns 409, which Alpine maps onto the name field — and the modal stays open.
            modal shouldRejectWith Messages.errorNameAlreadyExists()
        }

        "the record-matchers component validates a REGEX value and adds/removes a matcher" {
            val modal = openCreateModal()
            modal.openAssertionSettings().setNewMatcherMatchType("REGEX")

            // An invalid regex value shows the error and keeps the add (+) button disabled.
            modal.setNewMatcherValue("([")
            assertThat(modal.validationError(Messages.errorDnsRecordMatcherInvalid())).isVisible()
            assertThat(modal.addMatcherButton).isDisabled()

            // A valid regex clears the error and enables the add button.
            modal.setNewMatcherValue("^mail\\..*")
            assertThat(modal.validationError(Messages.errorDnsRecordMatcherInvalid())).hasCount(0)
            assertThat(modal.addMatcherButton).isEnabled()

            // Adding clears the entry input; removing drops the row.
            modal.addMatcherButton.click()
            assertThat(modal.matcherRow("^mail\\..*")).isVisible()
            assertThat(modal.newMatcherValueInput).hasValue("")

            modal.removeMatcher("^mail\\..*")
            assertThat(modal.matcherRow("^mail\\..*")).hasCount(0)
        }

        "a regex the browser accepts but the server rejects surfaces the server's message on the form" {
            val page = newPage()
            val list = DnsMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal().setName("DNS Server Side Regex").setHost("example.com")

            // `[]` is a valid JS regex (matches nothing) but an invalid Java one, so it passes the in-browser check
            // and can only be rejected by the server.
            modal.openAssertionSettings().setNewMatcherMatchType("REGEX")
            modal.addMatcher("[]")
            assertThat(modal.matcherRows).hasCount(1)

            modal.save()

            // The 400 must be explained on the form instead of leaving it silently open.
            assertThat(modal.formError).isVisible()
            assertThat(modal.formError).containsText("valid regular expression")
            assertThat(modal.saveButton).isVisible()
            dnsMonitorRepository.findByName("DNS Server Side Regex").shouldBeNull()
        }

        "a non-NOERROR response code combined with record matchers is flagged and blocks saving" {
            val modal = openCreateModal()
            modal.setName("DNS Conflict").setHost("example.com").addMatcher("1.2.3.4")

            // The cross-field rule only bites once both sides are set, so the matcher alone is still fine.
            assertThat(modal.validationError(Messages.errorDnsResponseCodeMatchersConflict())).hasCount(0)

            modal.selectResponseCode("NXDOMAIN")
            modal shouldRejectWith Messages.errorDnsResponseCodeMatchersConflict()

            // Dropping the matcher resolves the conflict without touching the response code.
            modal.removeMatcher("1.2.3.4")
            modal shouldAcceptAfterFixing Messages.errorDnsResponseCodeMatchersConflict()
        }
    }

    private fun openCreateModal(): DnsMonitorFormModal {
        val page = newPage()
        val list = DnsMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
