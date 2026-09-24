package com.kuvaszuptime.kuvasz.services.docker

import com.kuvaszuptime.kuvasz.models.dto.DockerHostValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostAuthMethod
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostDto
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS_TLS
import com.kuvaszuptime.kuvasz.testutils.DOCKER_TLS_DIR_PROPERTY
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.NoSuchBeanException
import java.nio.file.Files
import java.nio.file.Path

private fun readableTempFile(prefix: String): Path =
    Files.createTempFile(prefix, ".pem").also { it.toFile().deleteOnExit() }

class DockerHostRegistryTest : BehaviorSpec({

    val tlsDir = Files.createTempDirectory("docker-tls").also { it.toFile().deleteOnExit() }
    val pki = TestPki.generate(tlsDir)

    given("a valid docker-hosts configuration") {

        val ca = pki.caPem
        val cert = pki.clientCertPem
        val key = pki.clientKeyPem

        val ctx = testAppContext(
            mapOf(
                "docker-hosts" to listOf(
                    mapOf(
                        "name" to "vps-1", "url" to "tcp://10.0.0.5:2376", "tls" to mapOf(
                            "ca" to ca.toString(),
                            "cert" to cert.toString(),
                            "key" to key.toString(),
                        )
                    ),
                    mapOf("name" to "local", "url" to "unix:///var/run/docker.sock"),
                )
            )
        )
        val registry = ctx.getBean<DockerHostRegistry>()

        `when`("the hosts are resolved") {

            then("the unix host should be resolved into a socket address") {
                val host = registry["local"]
                host.shouldBeInstanceOf<DockerHost>()
                host.url shouldBe "unix:///var/run/docker.sock"
                host.tlsEnabled shouldBe false
                host.address.shouldBeInstanceOf<DockerDaemonAddress.UnixSocket>()
            }

            then("the TLS host should be resolved into a secure TCP address") {
                val host = registry["vps-1"]
                host.shouldBeInstanceOf<DockerHost>()
                host.tlsEnabled shouldBe true
                val address = host.address
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.host shouldBe "10.0.0.5"
                address.port shouldBe 2376
                address.tls shouldBe DockerTlsMaterial(
                    ca = ca,
                    clientCert = DockerClientCert(cert = cert, key = key),
                )
            }

            then("an unknown host should not be found") {
                registry["nope"].shouldBeNull()
            }

            then("the DTOs should expose the TLS flag but never the certificate paths, sorted by name") {
                registry.getConfiguredHostDtos(emptyMap()) shouldContainExactly listOf(
                    DockerHostDto(
                        name = "local",
                        url = "unix:///var/run/docker.sock",
                        tlsEnabled = false,
                        authMethod = DockerHostAuthMethod.UNIX_SOCKET,
                        apiVersion = null,
                    ),
                    DockerHostDto(
                        name = "vps-1",
                        url = "tcp://10.0.0.5:2376",
                        tlsEnabled = true,
                        authMethod = DockerHostAuthMethod.MUTUAL_TLS,
                        apiVersion = null,
                    ),
                )
            }

            // A version is only known once the client has talked to the host, and only for configured hosts
            then("the DTOs should carry the API versions negotiated so far") {
                val dtos = registry.getConfiguredHostDtos(mapOf("vps-1" to "1.44", "removed" to "1.40"))

                dtos.map { it.name to it.apiVersion } shouldContainExactly listOf("local" to null, "vps-1" to "1.44")
            }
        }
    }

    given("a docker-hosts configuration defined in YAML with TLS blocks") {

        val registry = testAppContext(mapOf(DOCKER_TLS_DIR_PROPERTY to tlsDir.toString()), DOCKER_HOSTS_TLS)
            .getBean<DockerHostRegistry>()

        `when`("a host configures the full mTLS material") {
            val address = registry["mtls"]?.address

            then("it should authenticate with the client certificate") {
                registry["mtls"]?.authMethod shouldBe DockerHostAuthMethod.MUTUAL_TLS
            }

            then("every path should be bound from the nested YAML block") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe 2376
                address.secure shouldBe true
                address.tls shouldBe DockerTlsMaterial(
                    ca = tlsDir.resolve("ca.pem"),
                    clientCert = DockerClientCert(
                        cert = tlsDir.resolve("cert.pem"),
                        key = tlsDir.resolve("key.pem"),
                    ),
                )
            }
        }

        `when`("a host configures only a CA") {
            val address = registry["ca-only"]?.address

            then("it should be encrypted, but not authenticate the client") {
                registry["ca-only"]?.authMethod shouldBe DockerHostAuthMethod.TLS
            }

            then("it should be secure without a client certificate, on the default TLS port") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe 2376
                address.secure shouldBe true
                address.tls shouldBe DockerTlsMaterial(ca = tlsDir.resolve("ca.pem"), clientCert = null)
            }
        }

        `when`("a host configures only a client certificate") {
            val address = registry["client-cert-only"]?.address

            then("it should still authenticate with the client certificate") {
                registry["client-cert-only"]?.authMethod shouldBe DockerHostAuthMethod.MUTUAL_TLS
            }

            then("it should present the certificate and rely on the system trust store") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.secure shouldBe true
                address.tls shouldBe DockerTlsMaterial(
                    ca = null,
                    clientCert = DockerClientCert(
                        cert = tlsDir.resolve("cert.pem"),
                        key = tlsDir.resolve("key.pem"),
                    ),
                )
            }
        }
    }

    given("no docker-hosts configuration at all") {

        val ctx = testAppContext()

        `when`("the registry is looked up") {
            then("the bean should not exist at all, because it is gated on the config beans") {
                shouldThrow<NoSuchBeanException> { ctx.getBean<DockerHostRegistry>() }
            }
        }
    }

    given("an invalid docker-hosts configuration") {

        `when`("two hosts share the same name") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "docker-hosts" to listOf(
                            mapOf("name" to "local", "url" to "unix:///var/run/docker.sock"),
                            mapOf("name" to "local", "url" to "tcp://10.0.0.5:2375"),
                        )
                    )
                )
            }

            then("the application should not start") {
                exception.message shouldContain "Duplicate Docker host configuration found for [local]"
            }
        }

        `when`("a host name is blank") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf("docker-hosts" to listOf(mapOf("name" to "", "url" to "tcp://10.0.0.5:2375")))
                )
            }

            then("the application should not start") {
                exception.message shouldContain DockerHostValidationMessages.NAME_NOT_BLANK
            }
        }

        `when`("a host URL is blank") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(mapOf("docker-hosts" to listOf(mapOf("name" to "local", "url" to ""))))
            }

            then("the application should not start") {
                exception.message shouldContain DockerHostValidationMessages.URL_NOT_BLANK
            }
        }

        `when`("a host URL uses an unsupported scheme") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf("docker-hosts" to listOf(mapOf("name" to "vps-1", "url" to "ssh://user@10.0.0.5")))
                )
            }

            then("the failure should name the offending host") {
                exception.message shouldContain "Invalid configuration for Docker host [vps-1]"
                exception.message shouldContain "SSH is not supported"
            }
        }

        `when`("the TLS block references a file that does not exist") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "docker-hosts" to listOf(
                            mapOf(
                                "name" to "vps-1", "url" to "tcp://10.0.0.5:2376", "tls" to mapOf(
                                    "ca" to readableTempFile("ca").toString(),
                                    "cert" to "/definitely/not/here/cert.pem",
                                    "key" to readableTempFile("key").toString(),
                                )
                            ),
                        )
                    )
                )
            }

            then("the failure should name the offending host and property") {
                exception.message shouldContain "Invalid configuration for Docker host [vps-1]"
                exception.message shouldContain "The TLS 'cert' file"
            }
        }

        `when`("the TLS block has a client certificate but no key") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "docker-hosts" to listOf(
                            mapOf(
                                "name" to "vps-1", "url" to "tcp://10.0.0.5:2376", "tls" to mapOf(
                                    "cert" to readableTempFile("cert").toString(),
                                )
                            ),
                        )
                    )
                )
            }

            then("the failure should explain that they belong together") {
                exception.message shouldContain "Invalid configuration for Docker host [vps-1]"
                exception.message shouldContain "The TLS 'cert' is configured without 'key'"
            }
        }

        `when`("the TLS block only configures a CA") {
            val ctx = testAppContext(
                mapOf(
                    "docker-hosts" to listOf(
                        mapOf(
                            "name" to "vps-1", "url" to "tcp://10.0.0.5:2376", "tls" to mapOf(
                                "ca" to pki.caPem.toString(),
                            )
                        ),
                    )
                )
            )

            then("the host should still be secure, just without a client certificate") {
                val host = ctx.getBean<DockerHostRegistry>()["vps-1"]
                host.shouldBeInstanceOf<DockerHost>()
                host.tlsEnabled shouldBe true
                val address = host.address
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.tls?.clientCert.shouldBeNull()
            }
        }
    }
})
