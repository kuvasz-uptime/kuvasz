package com.kuvaszuptime.kuvasz.services.docker.ssl

import com.kuvaszuptime.kuvasz.services.docker.TestPki
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator

private const val BYTE_MASK = 0xFF
private const val LONG_FORM_FLAG = 0x80
private const val LONG_FORM_COUNT_MASK = 0x7F
private const val BITS_PER_BYTE = 8
private const val RSA_KEY_SIZE = 2048

/** Just enough DER walking to pull the PKCS#1 body back out of a PKCS#8 wrapper, so a PKCS#1 fixture needs no tools. */
private class DerReader(private val bytes: ByteArray) {
    private var offset = 0

    fun next(): ByteArray {
        offset++ // tag
        var length = bytes[offset++].toInt() and BYTE_MASK
        if (length and LONG_FORM_FLAG != 0) {
            val count = length and LONG_FORM_COUNT_MASK
            length = 0
            repeat(count) { length = (length shl BITS_PER_BYTE) or (bytes[offset++].toInt() and BYTE_MASK) }
        }
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }
}

/** PrivateKeyInfo ::= SEQUENCE { version INTEGER, algorithm AlgorithmIdentifier, privateKey OCTET STRING } */
private fun pkcs1Of(pkcs8: ByteArray): ByteArray = DerReader(DerReader(pkcs8).next()).let { inner ->
    inner.next() // version
    inner.next() // algorithm
    inner.next() // the PKCS#1 RSAPrivateKey body
}

private fun keyPair(algorithm: String, size: Int? = null): KeyPair =
    KeyPairGenerator.getInstance(algorithm).apply { size?.let { initialize(it) } }.generateKeyPair()

private fun Path.writePem(label: String, der: ByteArray): Path =
    also { Files.writeString(it, TestPki.pem(label, der)) }

class DockerPemTest : BehaviorSpec({

    val directory = Files.createTempDirectory("kuvasz-pem")

    given("a private key file") {

        `when`("it holds a PKCS#8 RSA key") {
            val original = keyPair("RSA", RSA_KEY_SIZE).private
            val file = directory.resolve("pkcs8-rsa.pem").writePem("PRIVATE KEY", original.encoded)

            then("it should be read back unchanged") {
                DockerPem.readPrivateKey(file).encoded shouldBe original.encoded
            }
        }

        `when`("it holds a PKCS#8 EC key") {
            val original = keyPair("EC").private
            val file = directory.resolve("pkcs8-ec.pem").writePem("PRIVATE KEY", original.encoded)

            then("it should be read back unchanged") {
                DockerPem.readPrivateKey(file).encoded shouldBe original.encoded
            }
        }

        `when`("it holds a PKCS#1 RSA key, as 'openssl genrsa' produces on OpenSSL 1.x") {
            val original = keyPair("RSA", RSA_KEY_SIZE).private
            val file = directory.resolve("pkcs1-rsa.pem")
                .writePem("RSA PRIVATE KEY", pkcs1Of(original.encoded))

            then("it should be wrapped into PKCS#8 and yield the very same key") {
                DockerPem.readPrivateKey(file).encoded shouldBe original.encoded
            }
        }

        `when`("it is passphrase protected") {
            val file = directory.resolve("encrypted.pem")
                .writePem("ENCRYPTED PRIVATE KEY", byteArrayOf(1, 2, 3))

            then("the failure should explain how to decrypt it") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "passphrase-protected"
                exception.message shouldContain "openssl pkcs8 -topk8 -nocrypt"
            }
        }

        `when`("it is a SEC1 EC key, which the JDK cannot read") {
            val file = directory.resolve("sec1.pem").writePem("EC PRIVATE KEY", byteArrayOf(1, 2, 3))

            then("the failure should explain how to convert it") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "SEC1 EC format"
                exception.message shouldContain "openssl pkcs8 -topk8 -nocrypt"
            }
        }

        `when`("it is not a PEM key at all") {
            val file = directory.resolve("garbage.pem").also { Files.writeString(it, "not a key") }

            then("it should be rejected") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "not a PEM encoded private key"
            }
        }

        `when`("its footer is missing") {
            val file = directory.resolve("truncated.pem")
                .also { Files.writeString(it, "-----BEGIN PRIVATE KEY-----\nMIIE\n") }

            then("it should be rejected") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "missing its '-----END PRIVATE KEY-----' line"
            }
        }

        `when`("the PEM body is not valid base64") {
            val file = directory.resolve("bad-base64.pem")
                .also { Files.writeString(it, "-----BEGIN PRIVATE KEY-----\nnot really\n-----END PRIVATE KEY-----\n") }

            then("it should be rejected as a PEM problem, naming the file") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "[$file] is not valid base64"
            }
        }

        `when`("it is a binary DER key rather than a PEM text file") {
            val file = directory.resolve("key.der")
                .also { Files.write(it, keyPair("RSA", RSA_KEY_SIZE).private.encoded) }

            then("it should be rejected as a PEM problem, naming the file") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "[$file] could not be read as a PEM text file"
            }
        }

        `when`("the PEM body is not a readable key") {
            val file = directory.resolve("bogus-body.pem").writePem("PRIVATE KEY", byteArrayOf(1, 2, 3))

            then("it should name the algorithms that were tried") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readPrivateKey(file) }
                exception.message shouldContain "could not be read as any of: RSA, EC"
            }
        }
    }

    given("a certificate file") {

        val pki = TestPki.generate(Files.createTempDirectory("kuvasz-pem-pki"))

        `when`("it holds a single certificate") {
            then("it should be read") {
                DockerPem.readCertificates(pki.caPem) shouldHaveSize 1
            }
        }

        `when`("it holds a chain") {
            val chain = directory.resolve("chain.pem").also {
                Files.writeString(it, Files.readString(pki.caPem) + Files.readString(pki.clientCertPem))
            }

            then("every certificate should be read") {
                DockerPem.readCertificates(chain) shouldHaveSize 2
            }
        }

        `when`("it is empty") {
            val file = directory.resolve("empty.pem").also { Files.writeString(it, "") }

            then("it should be rejected as holding no certificate") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readCertificates(file) }
                exception.message shouldContain "does not contain any X.509 certificate"
            }
        }

        `when`("it cannot be opened") {
            val file = directory.resolve("gone.pem")

            then("the I/O failure should be surfaced as a PEM problem, naming the file") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readCertificates(file) }
                exception.message shouldContain "The certificate file [$file] could not be read"
            }
        }

        `when`("it holds something that is not a certificate") {
            val file = directory.resolve("garbage-cert.pem").also { Files.writeString(it, "nothing here") }

            then("the underlying parse failure should be surfaced") {
                val exception = shouldThrow<DockerPemException> { DockerPem.readCertificates(file) }
                exception.message shouldContain "could not be read"
            }
        }
    }
})
