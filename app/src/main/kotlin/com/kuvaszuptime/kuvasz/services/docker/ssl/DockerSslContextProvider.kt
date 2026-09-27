package com.kuvaszuptime.kuvasz.services.docker.ssl

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.DockerClientCert
import com.kuvaszuptime.kuvasz.services.docker.DockerDaemonAddress
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import com.kuvaszuptime.kuvasz.services.docker.DockerHostConfigException
import com.kuvaszuptime.kuvasz.services.docker.DockerHostRegistry
import com.kuvaszuptime.kuvasz.services.docker.DockerTlsMaterial
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * Builds one [SSLContext] per Docker host that configures TLS material, eagerly at startup, so malformed certificate
 * material is a boot failure rather than a check failure hours later.
 */
@Context
@Requires(bean = DockerHostConfig::class)
class DockerSslContextProvider(registry: DockerHostRegistry) {

    private companion object {
        const val PROTOCOL = "TLS"
        const val CA_ENTRY_PREFIX = "docker-ca"
        const val CLIENT_ENTRY = "docker-client"
        val EMPTY_PASSWORD = CharArray(0)
    }

    // Only a TCP address can carry TLS material, and one that does is always secure
    private val contexts: Map<String, SSLContext> = registry.configuredHosts.values
        .mapNotNull { host ->
            (host.address as? DockerDaemonAddress.Tcp)?.tls?.let { host.name to buildContext(host.name, it) }
        }
        .toMap()

    /**
     * The context to use for the given host. Falls back to the JVM default, which is what a `https://` host without
     * any configured TLS material needs: server verification against the system trust store, no client certificate.
     */
    fun forHost(host: DockerHost): SSLContext = contexts[host.name] ?: SSLContext.getDefault()

    private fun buildContext(hostName: String, material: DockerTlsMaterial): SSLContext = try {
        SSLContext.getInstance(PROTOCOL).apply {
            init(material.clientCert?.let(::keyManagers), material.ca?.let(::trustManagers), null)
        }
    } catch (ex: DockerPemException) {
        throw DockerHostConfigException("Invalid TLS material for Docker host [$hostName]: ${ex.message}", ex)
    }

    private fun trustManagers(ca: Path): Array<TrustManager> {
        val keyStore = emptyKeyStore()
        DockerPem.readCertificates(ca).forEachIndexed { index, certificate ->
            keyStore.setCertificateEntry("$CA_ENTRY_PREFIX-$index", certificate)
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
    }

    private fun keyManagers(clientCert: DockerClientCert): Array<KeyManager> {
        val chain = DockerPem.readCertificates(clientCert.cert).toTypedArray()
        val privateKey = DockerPem.readPrivateKey(clientCert.key)
        val keyStore = emptyKeyStore().apply { setKeyEntry(CLIENT_ENTRY, privateKey, EMPTY_PASSWORD, chain) }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, EMPTY_PASSWORD) }
            .keyManagers
    }

    private fun emptyKeyStore(): KeyStore =
        KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
}
