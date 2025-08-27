package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.simplejavamail.email.EmailBuilder
import java.util.concurrent.CompletableFuture

@SMTPTest
@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class EmailTestServiceTest(
    private val mailer: SMTPMailer,
    private val emailTestService: EmailTestService,
) : ShouldSpec({

    context("sendTestMessage") {

        should("call the mailer to send the message") {
            val testToAddress = "fdaf@fjdsfkd.com"
            val testFromAddress = "fl78fds@fdzs7809fd.com"
            val expectedMessage = EmailBuilder
                .startingBlank()
                .to(testToAddress, testToAddress)
                .from(testFromAddress, testFromAddress)
                .withSubject(Messages.integrationTestMessage())
                .withPlainText(Messages.integrationTestMessage())
                .buildEmail()
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns testToAddress
                every { fromAddress } returns testFromAddress
            }
            val mockMailer = getMock(mailer)
            every { mockMailer.sendAsync(expectedMessage) } returns CompletableFuture.completedFuture(null)

            val result = emailTestService.sendTestMessage(config).blockingGet()

            result.success shouldBe true
            result.message shouldBe Messages.successfulTestResultMessage()
            verify(exactly = 1) { mockMailer.sendAsync(expectedMessage) }
        }

        should("return a failed result when the mailer call fails") {
            val testToAddress = "fdaf@fjdsfkd.com"
            val testFromAddress = "fl78fds@fdzs7809fd.com"
            val expectedMessage = EmailBuilder
                .startingBlank()
                .to(testToAddress, testToAddress)
                .from(testFromAddress, testFromAddress)
                .withSubject(Messages.integrationTestMessage())
                .withPlainText(Messages.integrationTestMessage())
                .buildEmail()
            val config = mockk<EmailNotificationConfig>(relaxed = true) {
                every { toAddress } returns testToAddress
                every { fromAddress } returns testFromAddress
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
    }
}) {
    @MockBean(SMTPMailer::class)
    fun smtpMailer(): SMTPMailer = mockk()
}
