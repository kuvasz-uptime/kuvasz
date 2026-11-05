package com.kuvaszuptime.kuvasz.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.kuvaszuptime.kuvasz.controllers.integration.IntegrationClient
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.KGenericContainer
import com.kuvaszuptime.kuvasz.testutils.TestSMTPExtension.MAILPIT_HTTP_PORT
import com.kuvaszuptime.kuvasz.testutils.TestSMTPExtension.MAILPIT_IMAGE
import com.kuvaszuptime.kuvasz.testutils.TestSMTPExtension.MAILPIT_SMTP_PORT
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.reactive.awaitFirst
import java.net.URI
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration.Companion.seconds

class SMTPMailerConfigTest : BehaviorSpec(
    {
        given("an SMTPMailerConfig bean") {
            `when`("the SMTP host does not exists") {
                val properties = PropertySource.of(
                    "test",
                    mapOf(
                        "smtp-config.host" to "localhost",
                        "smtp-config.port" to "123"
                    )
                )
                then("ApplicationContext should throw a BeanInstantiationException") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(properties, "full-integrations-setup")
                    }
                    exception.message shouldContain "Unable to claim an SMTP transport"
                }
            }
        }

        given("an SMTP server that offers STARTTLS with an untrusted (self-signed) certificate") {
            val smtpServer = autoClose(
                KGenericContainer(MAILPIT_IMAGE)
                    .withEnv("MP_SMTP_TLS_CERT", "sans:localhost")
                    .withEnv("MP_SMTP_TLS_KEY", "sans:localhost")
                    .withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_HTTP_PORT)
                    .apply { start() }
            )

            `when`("the transport strategy is SMTP") {
                then("it should try to upgrade the connection with STARTTLS and fail") {
                    val exception = shouldThrow<BeanInstantiationException> {
                        testAppContext(
                            PropertySource.of(
                                "test",
                                mapOf(
                                    "smtp-config.host" to smtpServer.host,
                                    "smtp-config.port" to smtpServer.getMappedPort(MAILPIT_SMTP_PORT),
                                    "smtp-config.transport-strategy" to TransportStrategy.SMTP.name,
                                )
                            ),
                            "full-integrations-setup"
                        )
                    }
                    exception.message shouldContain "Unable to claim an SMTP transport"
                    generateSequence<Throwable>(exception) { it.cause }
                        .map { it::class }
                        .toList() shouldContain SSLHandshakeException::class
                }
            }

            `when`("the transport strategy is SMTP_PLAIN") {
                // The server would fail the STARTTLS handshake, so a delivered email proves that it was sent plain
                then("the app should start and deliver the test notification of an email integration") {
                    val server = shouldNotThrowAny {
                        autoClose(
                            ApplicationContext.run(
                                EmbeddedServer::class.java,
                                PropertySource.of(
                                    "test",
                                    mapOf(
                                        "smtp-plain-test-host" to smtpServer.host,
                                        "smtp-plain-test-port" to smtpServer.getMappedPort(MAILPIT_SMTP_PORT),
                                    )
                                ),
                                "test",
                                "smtp-plain",
                            )
                        )
                    }
                    val mailpitClient = autoClose(
                        server.applicationContext.createBean(
                            HttpClient::class.java,
                            URI("http://${smtpServer.host}:${smtpServer.getMappedPort(MAILPIT_HTTP_PORT)}")
                        )
                    )

                    val result = server.applicationContext.getBean<IntegrationClient>()
                        .sendTestNotification(IntegrationID(IntegrationType.EMAIL, "plain"))
                        .blockingGet()

                    result.success shouldBe true
                    eventually(5.seconds) {
                        val inbox = mailpitClient
                            .retrieve(HttpRequest.GET<Any>("/api/v1/messages"), MailpitMessages::class.java)
                            .awaitFirst()
                        val message = inbox.messages.single()
                        message.subject shouldBe Messages.integrationTestMessage()
                        message.from.address shouldBe "kuvasz@smtp-plain.test"
                        message.to.map { it.address } shouldBe listOf("ops@smtp-plain.test")
                    }
                }
            }
        }
    }
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MailpitMessages(val messages: List<MailpitMessage>)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MailpitMessage(
    @param:JsonProperty("Subject") val subject: String,
    @param:JsonProperty("From") val from: MailpitAddress,
    @param:JsonProperty("To") val to: List<MailpitAddress>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MailpitAddress(@param:JsonProperty("Address") val address: String)
