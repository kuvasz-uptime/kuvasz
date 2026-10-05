package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckerClientConfiguration
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLValidator
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.ssl.ClientSslConfiguration
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import jakarta.inject.Singleton
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

const val PROXY_E2E_ENV = "proxy-e2e"
const val PROXY_E2E_TRUST_STORE = "proxy-e2e.trust-store"
const val PROXY_E2E_TRUST_STORE_PASSWORD = "proxy-e2e.trust-store-password"

/**
 * Makes both the uptime and the SSL checks trust the throwaway CA of the proxy E2E specs, the same way an operator's
 * trust store would, without a test-only switch in the production code.
 */
@Factory
@Requires(env = [PROXY_E2E_ENV])
class ProxyE2ETrustFactory(
    @param:Value("\${$PROXY_E2E_TRUST_STORE}") private val trustStorePath: String,
    @param:Value("\${$PROXY_E2E_TRUST_STORE_PASSWORD}") private val trustStorePassword: String,
) {

    @Singleton
    @Replaces(HttpCheckerClientConfiguration::class)
    fun checkerClientConfiguration(
        applicationConfiguration: ApplicationConfiguration,
        appConfig: AppConfig,
    ): HttpCheckerClientConfiguration = HttpCheckerClientConfiguration(applicationConfiguration, appConfig).apply {
        sslConfiguration = ClientSslConfiguration().apply {
            trustStore = SslConfiguration.TrustStoreConfiguration().apply {
                setPath("file:$trustStorePath")
                setPassword(trustStorePassword)
                setType("PKCS12")
            }
        }
    }

    @Singleton
    @Replaces(SSLValidator::class)
    fun sslValidator(proxyTunnel: ProxyTunnel): SSLValidator {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            Path.of(trustStorePath).toFile().inputStream().use { load(it, trustStorePassword.toCharArray()) }
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers
        val socketFactory = SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }.socketFactory
        return SSLValidator(proxyTunnel, socketFactory)
    }
}
