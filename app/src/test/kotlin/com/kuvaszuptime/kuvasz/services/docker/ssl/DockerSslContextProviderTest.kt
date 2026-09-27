package com.kuvaszuptime.kuvasz.services.docker.ssl

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.DockerHostConfigException
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.TestPki
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.exceptions.BeanInstantiationException
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import javax.net.ssl.SSLContext

private fun tls(ca: String? = null, cert: String? = null, key: String? = null): DockerHostConfig.TlsConfig {
    val config = mockk<DockerHostConfig.TlsConfig>()
    every { config.ca } returns ca
    every { config.cert } returns cert
    every { config.key } returns key
    return config
}

private fun host(name: String, url: String, tlsConfig: DockerHostConfig.TlsConfig? = null): DockerHostConfig {
    val config = mockk<DockerHostConfig>()
    every { config.name } returns name
    every { config.url } returns url
    every { config.tls } returns tlsConfig
    return config
}

private fun providerFor(vararg configs: DockerHostConfig): Pair<DockerSslContextProvider, DockerHostRegistry> {
    val registry = DockerHostRegistry(configs.toList())
    return DockerSslContextProvider(registry) to registry
}

class DockerSslContextProviderTest : BehaviorSpec({

    val directory = Files.createTempDirectory("kuvasz-ssl")
    val pki = TestPki.generate(directory)

    given("hosts that need no certificate material") {

        val (provider, registry) = providerFor(
            host("local", "unix:///var/run/docker.sock"),
            host("plain", "tcp://10.0.0.5:2375"),
            host("system-trust", "https://10.0.0.6"),
        )

        `when`("their context is requested") {
            then("every one of them should fall back to the JVM default") {
                listOf("local", "plain", "system-trust").forEach { name ->
                    val resolved = registry[name].shouldNotBeNull()
                    provider.forHost(resolved) shouldBe SSLContext.getDefault()
                }
            }
        }
    }

    given("a host with valid TLS material") {

        val (provider, registry) = providerFor(
            host(
                "mtls",
                "tcp://10.0.0.5:2376",
                tls(pki.caPem.toString(), pki.clientCertPem.toString(), pki.clientKeyPem.toString()),
            ),
        )

        `when`("its context is requested") {
            then("it should get a dedicated context rather than the default one") {
                val resolved = registry["mtls"].shouldNotBeNull()
                provider.forHost(resolved) shouldNotBe SSLContext.getDefault()
            }
        }
    }

    given("a host whose certificate material is unreadable") {

        val brokenCert: Path = directory.resolve("broken-cert.pem")
            .also { Files.writeString(it, "-----BEGIN CERTIFICATE-----\nnot really\n-----END CERTIFICATE-----\n") }

        `when`("the context is built at startup") {
            val exception = shouldThrow<DockerHostConfigException> {
                providerFor(
                    host(
                        "broken",
                        "tcp://10.0.0.5:2376",
                        tls(cert = brokenCert.toString(), key = pki.clientKeyPem.toString()),
                    ),
                )
            }

            then("the application should not start, and the failure should name the host") {
                exception.message shouldContain "Invalid TLS material for Docker host [broken]"
            }
        }
    }

    given("a host whose private key is unreadable") {

        val brokenKey: Path = directory.resolve("broken-key.pem")
            .also { Files.writeString(it, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n") }

        `when`("the context is built at startup") {
            val exception = shouldThrow<DockerHostConfigException> {
                providerFor(
                    host(
                        "broken-key",
                        "tcp://10.0.0.5:2376",
                        tls(cert = pki.clientCertPem.toString(), key = brokenKey.toString()),
                    ),
                )
            }

            then("the application should not start, and the failure should name the host") {
                exception.message shouldContain "Invalid TLS material for Docker host [broken-key]"
                exception.message shouldContain "could not be read as any of"
            }
        }
    }

    given("a host whose private key is not valid base64") {

        val badBase64Key: Path = directory.resolve("bad-base64-key.pem")
            .also { Files.writeString(it, "-----BEGIN PRIVATE KEY-----\nnot really\n-----END PRIVATE KEY-----\n") }

        `when`("the context is built at startup") {
            val exception = shouldThrow<DockerHostConfigException> {
                providerFor(
                    host(
                        "bad-base64",
                        "tcp://10.0.0.5:2376",
                        tls(cert = pki.clientCertPem.toString(), key = badBase64Key.toString()),
                    ),
                )
            }

            then("the failure should still name the host, instead of escaping as a bare decoding error") {
                exception.message shouldContain "Invalid TLS material for Docker host [bad-base64]"
                exception.message shouldContain "is not valid base64"
            }
        }
    }

    given("an application whose Docker host points at an empty CA file") {

        val emptyCa = Files.createFile(directory.resolve("empty-ca.pem"))

        `when`("it starts") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "docker-hosts" to listOf(
                            mapOf(
                                "name" to "vps-1",
                                "url" to "tcp://10.0.0.5:2376",
                                "tls" to mapOf("ca" to emptyCa.toString()),
                            ),
                        )
                    )
                )
            }

            then("it should fail to boot, because the contexts are built eagerly rather than at the first check") {
                exception.message shouldContain "Invalid TLS material for Docker host [vps-1]"
                exception.message shouldContain "does not contain any X.509 certificate"
            }
        }
    }

    given("only a CA, with no client certificate") {

        val (provider, registry) = providerFor(
            host("ca-only", "tcp://10.0.0.5:2376", tls(ca = pki.caPem.toString())),
        )

        `when`("its context is requested") {
            then("a context should still be built, trusting that CA") {
                val resolved = registry["ca-only"].shouldNotBeNull()
                resolved.tlsEnabled shouldBe true
                provider.forHost(resolved) shouldNotBe SSLContext.getDefault()
            }
        }
    }
})
