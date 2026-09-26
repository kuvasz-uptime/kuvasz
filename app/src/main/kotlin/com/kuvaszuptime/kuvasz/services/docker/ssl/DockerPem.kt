package com.kuvaszuptime.kuvasz.services.docker.ssl

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.HexFormat

class DockerPemException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Reads the PEM files a Docker host's TLS configuration points at, using nothing but the JDK.
 *
 * The JDK's `KeyFactory` only understands PKCS#8 (`BEGIN PRIVATE KEY`), while the client keys produced by the
 * `openssl genrsa` command in Docker's own TLS guide are PKCS#1 (`BEGIN RSA PRIVATE KEY`) on OpenSSL 1.x. Rather than
 * pulling in BouncyCastle for it — which is the reason docker-java is as heavy as it is — a PKCS#1 body is wrapped
 * into a PKCS#8 `PrivateKeyInfo`, which is a fixed, purely structural transformation.
 */
internal object DockerPem {

    private const val PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----"
    private const val PKCS8_FOOTER = "-----END PRIVATE KEY-----"
    private const val PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----"
    private const val PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----"
    private const val SEC1_HEADER = "-----BEGIN EC PRIVATE KEY-----"
    private const val ENCRYPTED_HEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----"

    private const val DER_SEQUENCE = 0x30
    private const val DER_OCTET_STRING = 0x04
    private const val DER_LONG_FORM = 0x80

    // AlgorithmIdentifier for rsaEncryption (OID 1.2.840.113549.1.1.1) with NULL parameters, and INTEGER 0
    private val RSA_ALGORITHM_ID = HexFormat.of().parseHex("300d06092a864886f70d0101010500")
    private val PKCS8_VERSION = HexFormat.of().parseHex("020100")

    private val KEY_ALGORITHMS = listOf("RSA", "EC")

    /**
     * @throws DockerPemException if the file cannot be read or holds no X.509 certificate.
     */
    fun readCertificates(path: Path): Collection<X509Certificate> =
        parseCertificates(path).filterIsInstance<X509Certificate>().ifEmpty {
            throw DockerPemException("The certificate file [$path] does not contain any X.509 certificate.")
        }

    private fun parseCertificates(path: Path): Collection<Certificate> = try {
        Files.newInputStream(path).use { input -> CertificateFactory.getInstance("X.509").generateCertificates(input) }
    } catch (ex: GeneralSecurityException) {
        throw unreadableCertificate(path, ex)
    } catch (ex: IOException) {
        throw unreadableCertificate(path, ex)
    }

    private fun unreadableCertificate(path: Path, cause: Exception): DockerPemException =
        DockerPemException("The certificate file [$path] could not be read: ${cause.message}", cause)

    /**
     * @throws DockerPemException if the file cannot be read, or the key is in a format the JDK cannot read on its own.
     */
    fun readPrivateKey(path: Path): PrivateKey {
        val content = try {
            Files.readString(path)
        } catch (ex: IOException) {
            // A binary DER key ends up here too, as it is not valid UTF-8
            throw DockerPemException("The private key [$path] could not be read as a PEM text file: ${ex.message}", ex)
        }
        val der = when {
            content.contains(PKCS8_HEADER) -> decodeBlock(path, content, PKCS8_HEADER, PKCS8_FOOTER)
            content.contains(PKCS1_HEADER) ->
                wrapPkcs1(decodeBlock(path, content, PKCS1_HEADER, PKCS1_FOOTER))

            else -> throw unsupportedKeyFormat(path, content)
        }
        return toPrivateKey(path, der)
    }

    private fun unsupportedKeyFormat(path: Path, content: String): DockerPemException = when {
        content.contains(ENCRYPTED_HEADER) -> DockerPemException(
            "The private key [$path] is passphrase-protected, which is not supported. Decrypt it with " +
                "'openssl pkcs8 -topk8 -nocrypt -in $path -out key.pem'."
        )

        content.contains(SEC1_HEADER) -> DockerPemException(
            "The private key [$path] is in the SEC1 EC format, which the JDK cannot read. Convert it with " +
                "'openssl pkcs8 -topk8 -nocrypt -in $path -out key.pem'."
        )

        else -> DockerPemException(
            "The private key [$path] is not a PEM encoded private key. Expected a '$PKCS8_HEADER' or " +
                "'$PKCS1_HEADER' block."
        )
    }

    private fun decodeBlock(path: Path, content: String, header: String, footer: String): ByteArray {
        val start = content.indexOf(header) + header.length
        val end = content.indexOf(footer, start)
        if (end < 0) {
            throw DockerPemException("The private key [$path] is missing its '$footer' line.")
        }
        return try {
            Base64.getMimeDecoder().decode(content.substring(start, end))
        } catch (ex: IllegalArgumentException) {
            throw DockerPemException("The private key [$path] is not valid base64: ${ex.message}", ex)
        }
    }

    private fun toPrivateKey(path: Path, der: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(der)
        KEY_ALGORITHMS.forEach { algorithm ->
            runCatching { KeyFactory.getInstance(algorithm).generatePrivate(spec) }
                .getOrNull()
                ?.let { return it }
        }
        throw DockerPemException(
            "The private key [$path] could not be read as any of: ${KEY_ALGORITHMS.joinToString(", ")}."
        )
    }

    /**
     * Wraps a PKCS#1 `RSAPrivateKey` body into a PKCS#8 `PrivateKeyInfo`, which is what the JDK's KeyFactory expects.
     */
    private fun wrapPkcs1(pkcs1: ByteArray): ByteArray = der(
        DER_SEQUENCE,
        PKCS8_VERSION + RSA_ALGORITHM_ID + der(DER_OCTET_STRING, pkcs1),
    )

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val length = if (content.size < DER_LONG_FORM) {
            byteArrayOf(content.size.toByte())
        } else {
            val encoded = content.size.toBigInteger().toByteArray().dropWhile { it == ZERO_BYTE }.toByteArray()
            byteArrayOf((DER_LONG_FORM or encoded.size).toByte()) + encoded
        }
        return byteArrayOf(tag.toByte()) + length + content
    }

    private const val ZERO_BYTE: Byte = 0
}
