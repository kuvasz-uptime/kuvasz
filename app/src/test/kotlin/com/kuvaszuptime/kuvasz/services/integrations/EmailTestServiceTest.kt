package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.Message
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.recipient.RecipientBuilder
import java.util.concurrent.CompletableFuture

private const val TEST_FROM_ADDRESS = "fl78fds@fdzs7809fd.com"

@SMTPTest
@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class EmailTestServiceTest(
    private val mailer: SMTPMailer,
    private val emailTestService: EmailTestService,
) : ShouldSpec({

    context("sendTestMessage") {

        should("call the mailer to send the message") {
            val testToAddress = "fdaf@fjdsfkd.com"
            val expectedMessage = expectedTestEmail(testToAddress)
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns listOf(testToAddress)
                every { fromAddress } returns TEST_FROM_ADDRESS
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(expectedMessage) } returns CompletableFuture.completedFuture(null)

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
        }

        should("use the from name as the display name of the sender if it's set") {
            val testToAddress = "fdaf@fjdsfkd.com"
            val expectedMessage = expectedTestEmail(testToAddress, fromName = "Kuvasz Alerts")
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns listOf(testToAddress)
                every { fromAddress } returns TEST_FROM_ADDRESS
                every { fromName } returns " Kuvasz Alerts "
            }
            val sentEmail = slot<Email>()
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(capture(sentEmail)) } returns CompletableFuture.completedFuture(null)

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            sentEmail.captured.fromRecipient.shouldNotBeNull().name shouldBe "Kuvasz Alerts"
            verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
        }

        listOf(null, "", " ").forEach { missingName ->
            should("use the from address as the display name of the sender if the from name is '$missingName'") {
                val testToAddress = "fdaf@fjdsfkd.com"
                val expectedMessage = expectedTestEmail(testToAddress)
                val config = mockk<EmailNotificationConfig>(relaxed = true) {
                    every { toAddress } returns listOf(testToAddress)
                    every { fromAddress } returns TEST_FROM_ADDRESS
                    every { fromName } returns missingName
                }
                val sentEmail = slot<Email>()
                val mockMailer = getMock(mailer)
                every { mockMailer.sendAsync(capture(sentEmail)) } returns CompletableFuture.completedFuture(null)

                val result = emailTestService.sendTestMessage(config).blockingGet()

                result.success shouldBe true
                sentEmail.captured.fromRecipient.shouldNotBeNull().name shouldBe TEST_FROM_ADDRESS
                verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
            }
        }

        should("send one message with every recipient in To if there are multiple recipients") {
            val testToAddresses = listOf("first@fjdsfkd.com", "second@fjdsfkd.com")
            val expectedMessage = expectedTestEmail(*testToAddresses.toTypedArray())
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns testToAddresses
                every { fromAddress } returns TEST_FROM_ADDRESS
                every { separateEmails } returns false
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(expectedMessage) } returns CompletableFuture.completedFuture(null)

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            verify(exactly = 1) { mockMailer.sendAsync(any()) }
            verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
        }

        should("send a separate message to every recipient if separate emails are enabled") {
            val testToAddresses = listOf("first@fjdsfkd.com", "second@fjdsfkd.com", "third@fjdsfkd.com")
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns testToAddresses
                every { fromAddress } returns TEST_FROM_ADDRESS
                every { separateEmails } returns true
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(any()) } returns CompletableFuture.completedFuture(null)

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verify(exactly = testToAddresses.size) { mockMailer.sendAsync(any()) }
            testToAddresses.forEach { address ->
                verify(exactly = 1) { mockMailer.sendAsync(expectedTestEmail(address)) }
            }
        }

        should("return a failed result when the mailer call fails") {
            val testToAddress = "fdaf@fjdsfkd.com"
            val expectedMessage = expectedTestEmail(testToAddress)
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns listOf(testToAddress)
                every { fromAddress } returns TEST_FROM_ADDRESS
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(expectedMessage) } returns CompletableFuture.failedFuture(
                RuntimeException("Something went wrong")
            )

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe
                Messages.failedTestResultMessage("java.lang.RuntimeException: Something went wrong")
            verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
        }

        should("return a failed result when only one of the separate messages fails") {
            val failingAddress = "second@fjdsfkd.com"
            val testToAddresses = listOf("first@fjdsfkd.com", failingAddress, "third@fjdsfkd.com")
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns testToAddresses
                every { fromAddress } returns TEST_FROM_ADDRESS
                every { separateEmails } returns true
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(any()) } returns CompletableFuture.completedFuture(null)
            every { mockMailer.sendAsync(expectedTestEmail(failingAddress)) } returns CompletableFuture.failedFuture(
                RuntimeException("Something went wrong")
            )

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe false
            result.message shouldBe
                Messages.failedTestResultMessage("java.lang.RuntimeException: Something went wrong")
            verify(exactly = testToAddresses.size) { mockMailer.sendAsync(any()) }
        }
    }
}) {
    @MockBean(SMTPMailer::class)
    fun smtpMailer(): SMTPMailer = mockk()
}

private fun expectedTestEmail(vararg toAddresses: String, fromName: String = TEST_FROM_ADDRESS): Email =
    EmailBuilder
        .startingBlank()
        .withRecipients(
            toAddresses.map { address ->
                RecipientBuilder()
                    .withAddress(address)
                    .withType(Message.RecipientType.TO)
                    .build()
            }
        )
        .from(fromName, TEST_FROM_ADDRESS)
        .withSubject(Messages.integrationTestMessage())
        .withPlainText(Messages.integrationTestMessage())
        .buildEmail()
