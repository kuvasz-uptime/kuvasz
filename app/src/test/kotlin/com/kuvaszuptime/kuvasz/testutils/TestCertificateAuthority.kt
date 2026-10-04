package com.kuvaszuptime.kuvasz.testutils

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * A throwaway certificate authority, built with the JDK's own `keytool`, so nothing resembling key material is
 * committed and the specs do not depend on `openssl` being installed.
 *
 * Unlike a self-signed server certificate, a certificate issued by a CA is validated like a real one: a trust anchor's
 * own validity is never checked, so an expired certificate is only rejected if it is not the anchor itself.
 */
class TestCertificateAuthority private constructor(private val directory: Path) {

    /**
     * A server certificate in PEM, with the CA's certificate appended, and its private key.
     */
    data class IssuedCertificate(val chainPem: Path, val keyPem: Path)

    companion object {
        private const val PASSWORD = "changeit"
        private const val CA_ALIAS = "ca"
        private const val PEM_LINE_LENGTH = 64

        fun generate(directory: Path): TestCertificateAuthority = TestCertificateAuthority(directory).apply {
            keytool(
                "-genkeypair", "-alias", CA_ALIAS, "-dname", "CN=kuvasz-test-ca", "-ext", "bc:c",
                "-keyalg", "RSA", "-keysize", "2048", "-startdate", "-1d", "-validity", "365",
                "-keystore", caKeystore.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD,
            )
        }

        private fun pem(label: String, der: ByteArray): String {
            val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(der)
            return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
        }
    }

    private val caKeystore: Path = directory.resolve("ca.p12")

    private val caCertificate: X509Certificate by lazy {
        loadKeyStore(caKeystore).getCertificate(CA_ALIAS) as X509Certificate
    }

    /**
     * A PKCS12 trust store with the CA's certificate in it.
     */
    val trustStore: Path by lazy {
        directory.resolve("truststore.p12").also { file ->
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setCertificateEntry(CA_ALIAS, caCertificate)
                Files.newOutputStream(file).use { store(it, PASSWORD.toCharArray()) }
            }
        }
    }

    val trustStorePassword: String = PASSWORD

    /**
     * Issues a server certificate for [dnsName].
     *
     * @param startDate relative to now in keytool's format, e.g. `-3d`, which makes an already expired certificate
     * together with a short [validityDays].
     */
    fun issue(name: String, dnsName: String, startDate: String = "-1d", validityDays: Int = 3): IssuedCertificate {
        val keystore = directory.resolve("$name.p12")
        val csr = directory.resolve("$name.csr")
        val certificate = directory.resolve("$name.crt")
        keytool(
            "-genkeypair", "-alias", name, "-dname", "CN=$dnsName",
            "-keyalg", "RSA", "-keysize", "2048",
            "-keystore", keystore.toString(), "-storetype", "PKCS12",
            "-storepass", PASSWORD, "-keypass", PASSWORD,
        )
        keytool(
            "-certreq", "-alias", name, "-file", csr.toString(),
            "-keystore", keystore.toString(), "-storepass", PASSWORD,
        )
        keytool(
            "-gencert", "-alias", CA_ALIAS, "-infile", csr.toString(), "-outfile", certificate.toString(), "-rfc",
            "-ext", "san=dns:$dnsName", "-startdate", startDate, "-validity", validityDays.toString(),
            "-keystore", caKeystore.toString(), "-storepass", PASSWORD,
        )

        val leaf = Files.newInputStream(certificate).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val key = loadKeyStore(keystore).getKey(name, PASSWORD.toCharArray()) as PrivateKey
        return IssuedCertificate(
            chainPem = directory.resolve("$name-chain.pem").also {
                Files.writeString(it, pem("CERTIFICATE", leaf.encoded) + pem("CERTIFICATE", caCertificate.encoded))
            },
            keyPem = directory.resolve("$name.key").also { Files.writeString(it, pem("PRIVATE KEY", key.encoded)) },
        )
    }

    private fun loadKeyStore(file: Path): KeyStore = KeyStore.getInstance("PKCS12").apply {
        Files.newInputStream(file).use { load(it, PASSWORD.toCharArray()) }
    }

    private fun keytool(vararg arguments: String) {
        val command = listOf(Path.of(System.getProperty("java.home"), "bin", "keytool").toString()) + arguments
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "keytool failed: $output" }
    }
}
