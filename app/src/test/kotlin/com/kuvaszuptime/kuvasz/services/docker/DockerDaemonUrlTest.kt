package com.kuvaszuptime.kuvasz.services.docker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_PLAIN_PORT = 2375
private const val DEFAULT_TLS_PORT = 2376

private fun readableTempFile(prefix: String): Path =
    Files.createTempFile(prefix, ".pem").also { it.toFile().deleteOnExit() }

private fun tlsMaterial(): DockerTlsMaterial = DockerTlsMaterial(
    ca = readableTempFile("ca"),
    clientCert = DockerClientCert(cert = readableTempFile("cert"), key = readableTempFile("key")),
)

class DockerDaemonUrlTest : BehaviorSpec({

    given("a unix socket URL") {

        `when`("it is well-formed") {
            val address = DockerDaemonUrl.parse("unix:///var/run/docker.sock", tls = null)

            then("it should be parsed into a socket path") {
                address.shouldBeInstanceOf<DockerDaemonAddress.UnixSocket>()
                address.path shouldBe Path.of("/var/run/docker.sock")
            }
        }

        `when`("it is surrounded by whitespace") {
            val address = DockerDaemonUrl.parse("  unix:///var/run/docker.sock  ", tls = null)

            then("it should still be parsed") {
                address.shouldBeInstanceOf<DockerDaemonAddress.UnixSocket>()
                address.path shouldBe Path.of("/var/run/docker.sock")
            }
        }

        `when`("TLS material is configured for it") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("unix:///var/run/docker.sock", tls = tlsMaterial())
            }

            then("it should be rejected, because the connection is local") {
                exception.message shouldContain "TLS material cannot be used with the unix socket URL"
            }
        }

        `when`("it has only two slashes, so the path ends up as an authority") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("unix://var/run/docker.sock", tls = null)
            }

            then("it should be rejected with a hint about the missing slash") {
                exception.message shouldContain "looks like it is missing a slash"
            }
        }

        `when`("it is opaque, so it carries no path") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("unix:var/run/docker.sock", tls = null)
            }

            then("it should be rejected") {
                exception.message shouldContain "does not contain a socket path"
            }
        }
    }

    given("a TCP URL") {

        `when`("it has an explicit port and no TLS material") {
            val address = DockerDaemonUrl.parse("tcp://10.0.0.5:2375", tls = null)

            then("it should be parsed as a plaintext connection") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.host shouldBe "10.0.0.5"
                address.port shouldBe DEFAULT_PLAIN_PORT
                address.secure shouldBe false
                address.tls.shouldBeNull()
            }
        }

        `when`("it has no port and no TLS material") {
            val address = DockerDaemonUrl.parse("tcp://10.0.0.5", tls = null)

            then("it should fall back to the default plaintext port") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe DEFAULT_PLAIN_PORT
                address.secure shouldBe false
            }
        }

        `when`("it has no port but TLS material is configured") {
            val material = tlsMaterial()
            val address = DockerDaemonUrl.parse("tcp://10.0.0.5", tls = material)

            then("it should fall back to the default TLS port and be secure") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe DEFAULT_TLS_PORT
                address.secure shouldBe true
                address.tls shouldBe material
            }
        }

        `when`("its host name has an underscore, like a Compose service name") {
            val address = DockerDaemonUrl.parse("tcp://docker_proxy:2375", tls = null)

            then("it should still be parsed, although java.net.URI does not consider it a host name") {
                address shouldBe DockerDaemonAddress.Tcp(
                    host = "docker_proxy",
                    port = DEFAULT_PLAIN_PORT,
                    secure = false,
                    tls = null,
                )
            }
        }

        `when`("its host name has an underscore and there is no port") {
            val address = DockerDaemonUrl.parse("https://docker_proxy", tls = null)

            then("it should fall back to the default port of the scheme") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.host shouldBe "docker_proxy"
                address.port shouldBe DEFAULT_TLS_PORT
            }
        }

        `when`("its port is out of range") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("tcp://10.0.0.5:99999", tls = null)
            }

            then("it should be rejected at startup rather than failing every check") {
                exception.message shouldContain "has an invalid port [99999]"
                exception.message shouldContain "Expected one between 1 and 65535"
            }
        }

        `when`("its underscored host name comes with port 0") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("tcp://docker_proxy:0", tls = null)
            }

            then("the lenient parsing should be range-checked too") {
                exception.message shouldContain "has an invalid port [0]"
            }
        }

        `when`("its port is not numeric, so the authority cannot be parsed into a host") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("tcp://10.0.0.5:not-a-port", tls = null)
            }

            then("it should be rejected") {
                exception.message shouldContain "does not contain a host name"
            }
        }
    }

    given("an HTTP(S) URL") {

        `when`("it uses the http scheme") {
            val address = DockerDaemonUrl.parse("http://10.0.0.5", tls = null)

            then("it should be treated as a plaintext TCP connection") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe DEFAULT_PLAIN_PORT
                address.secure shouldBe false
            }
        }

        `when`("it uses the http scheme but TLS material is configured") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("http://10.0.0.5", tls = tlsMaterial())
            }

            then("it should be rejected as contradictory") {
                exception.message shouldContain "uses the plaintext 'http' scheme"
            }
        }

        `when`("it uses the https scheme without TLS material") {
            val address = DockerDaemonUrl.parse("https://10.0.0.5", tls = null)

            then("it should be secure, relying on the system trust store") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe DEFAULT_TLS_PORT
                address.secure shouldBe true
                address.tls.shouldBeNull()
            }
        }

        `when`("it uses the https scheme with an explicit port") {
            val address = DockerDaemonUrl.parse("https://10.0.0.5:8443", tls = null)

            then("the explicit port should win") {
                address.shouldBeInstanceOf<DockerDaemonAddress.Tcp>()
                address.port shouldBe 8443
            }
        }
    }

    given("an unsupported URL") {

        `when`("it uses the ssh scheme") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("ssh://user@10.0.0.5", tls = null)
            }

            then("it should be rejected with an explicit note about SSH") {
                exception.message shouldContain "SSH is not supported"
            }
        }

        `when`("it uses an entirely unknown scheme") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("ftp://10.0.0.5", tls = null)
            }

            then("it should be rejected, listing every supported scheme") {
                exception.message shouldContain "unsupported scheme [ftp]"
                exception.message shouldContain "Expected one of: [unix://, tcp://, http://, https://]"
            }
        }

        `when`("it has no scheme at all") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("/var/run/docker.sock", tls = null)
            }

            then("it should be rejected") {
                exception.message shouldContain "is missing a scheme"
            }
        }

        `when`("it is not a valid URL") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.parse("tcp://", tls = null)
            }

            then("it should be rejected") {
                exception.message shouldContain "is not a valid URL"
            }
        }
    }

    given("a TLS configuration block") {

        `when`("it is entirely absent") {
            then("it should resolve to null") {
                DockerDaemonUrl.resolveTlsMaterial(ca = null, cert = null, key = null).shouldBeNull()
            }
        }

        `when`("every property is blank") {
            then("it should resolve to null") {
                DockerDaemonUrl.resolveTlsMaterial(ca = "  ", cert = "", key = " ").shouldBeNull()
            }
        }

        `when`("only a CA is configured") {
            val ca = readableTempFile("ca")
            val material = DockerDaemonUrl.resolveTlsMaterial(ca = ca.toString(), cert = null, key = null)

            then("it should verify the daemon without presenting a client certificate") {
                material shouldBe DockerTlsMaterial(ca = ca, clientCert = null)
            }
        }

        `when`("only a client certificate and its key are configured") {
            val cert = readableTempFile("cert")
            val key = readableTempFile("key")
            val material = DockerDaemonUrl.resolveTlsMaterial(
                ca = null,
                cert = cert.toString(),
                key = key.toString(),
            )

            then("it should authenticate against a publicly trusted daemon certificate") {
                material shouldBe DockerTlsMaterial(
                    ca = null,
                    clientCert = DockerClientCert(cert = cert, key = key),
                )
            }
        }

        `when`("all three are configured") {
            val ca = readableTempFile("ca")
            val cert = readableTempFile("cert")
            val key = readableTempFile("key")
            val material = DockerDaemonUrl.resolveTlsMaterial(
                ca = ca.toString(),
                cert = cert.toString(),
                key = key.toString(),
            )

            then("it should resolve into a full mTLS setup") {
                material shouldBe DockerTlsMaterial(
                    ca = ca,
                    clientCert = DockerClientCert(cert = cert, key = key),
                )
            }
        }

        `when`("a client certificate is configured without its key") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.resolveTlsMaterial(ca = null, cert = readableTempFile("cert").toString(), key = null)
            }

            then("it should be rejected, because they only make sense as a pair") {
                exception.message shouldContain "The TLS 'cert' is configured without 'key'"
            }
        }

        `when`("a key is configured without its client certificate") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.resolveTlsMaterial(ca = null, cert = null, key = readableTempFile("key").toString())
            }

            then("it should be rejected, because they only make sense as a pair") {
                exception.message shouldContain "The TLS 'key' is configured without 'cert'"
            }
        }

        `when`("one of the paths is not representable on the filesystem") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.resolveTlsMaterial(ca = "ca\u0000.pem", cert = null, key = null)
            }

            then("it should name the offending property") {
                exception.message shouldContain "The TLS 'ca' path"
                exception.message shouldContain "is not valid"
            }
        }

        `when`("one of the files does not exist") {
            val exception = shouldThrow<DockerHostConfigException> {
                DockerDaemonUrl.resolveTlsMaterial(
                    ca = readableTempFile("ca").toString(),
                    cert = "/definitely/not/here/cert.pem",
                    key = readableTempFile("key").toString(),
                )
            }

            then("it should name the offending property") {
                exception.message shouldContain "The TLS 'cert' file"
                exception.message shouldContain "does not exist or is not readable"
            }
        }
    }
})
