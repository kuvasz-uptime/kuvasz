package com.kuvaszuptime.kuvasz.services.docker

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Generates throwaway TLS material with the JDK's own `keytool`, so nothing resembling key material is committed and
 * the specs do not depend on `openssl` being installed.
 *
 * Both certificates are self-signed, which is exactly the shape the "custom CA" case takes in practice: the client
 * trusts the daemon's own certificate by pointing `ca` at it, and the daemon trusts the client's certificate.
 *
 * The PEM files use the names Docker itself expects in a cert directory: `ca.pem`, `cert.pem` and `key.pem`.
 */
internal class TestPki private constructor(
    val serverSslContext: SSLContext,
    val caPem: Path,
    val clientCertPem: Path,
    val clientKeyPem: Path,
) {

    companion object {

        private const val PASSWORD = "changeit"
        private const val PEM_LINE_LENGTH = 64

        fun generate(directory: Path): TestPki {
            val serverStore = keyStore(directory, "server", "CN=localhost", "san=dns:localhost,ip:127.0.0.1")
            val clientStore = keyStore(directory, "client", "CN=kuvasz-client", null)

            val serverCertificate = serverStore.getCertificate("server") as X509Certificate
            val clientCertificate = clientStore.getCertificate("client") as X509Certificate

            val caPem = directory.resolve("ca.pem").also { it.writeCertificate(serverCertificate) }
            val clientCertPem = directory.resolve("cert.pem").also { it.writeCertificate(clientCertificate) }
            val clientKey = clientStore.getKey("client", PASSWORD.toCharArray()) as PrivateKey
            val clientKeyPem = directory.resolve("key.pem").also { it.writePkcs8Key(clientKey) }

            return TestPki(
                serverSslContext = serverSslContext(serverStore, clientCertificate),
                caPem = caPem,
                clientCertPem = clientCertPem,
                clientKeyPem = clientKeyPem,
            )
        }

        private fun keyStore(directory: Path, alias: String, dn: String, extension: String?): KeyStore {
            val file = directory.resolve("$alias.p12")
            val command = mutableListOf(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", alias, "-dname", dn,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-keystore", file.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD,
            )
            extension?.let { command += listOf("-ext", it) }

            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "keytool failed for [$alias]: $output" }

            return KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(file).use { load(it, PASSWORD.toCharArray()) }
            }
        }

        private fun serverSslContext(serverStore: KeyStore, clientCertificate: X509Certificate): SSLContext {
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("client", clientCertificate)
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(serverStore, PASSWORD.toCharArray()) }
                .keyManagers
            val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(trustStore) }
                .trustManagers
            return SSLContext.getInstance("TLS").apply { init(keyManagers, trustManagers, null) }
        }

        private fun Path.writeCertificate(certificate: X509Certificate) =
            Files.writeString(this, pem("CERTIFICATE", certificate.encoded))

        private fun Path.writePkcs8Key(key: PrivateKey) =
            Files.writeString(this, pem("PRIVATE KEY", key.encoded))

        fun pem(label: String, der: ByteArray): String {
            val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(der)
            return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
        }
    }
}
