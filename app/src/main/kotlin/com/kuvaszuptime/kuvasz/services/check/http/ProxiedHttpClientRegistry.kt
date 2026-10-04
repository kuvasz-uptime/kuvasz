package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.services.check.tcp.BoundedHostnameResolver
import com.kuvaszuptime.kuvasz.services.proxy.ConfiguredProxy
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The configuration of the client that checks HTTP monitors through [proxy]. Apart from the route, it is identical
 * to the one of the direct client.
 */
class ProxiedHttpCheckerClientConfiguration(
    config: ApplicationConfiguration,
    appConfig: AppConfig,
    private val proxy: ConfiguredProxy,
    numOfThreads: Int,
) : BaseHttpCheckerClientConfiguration(config, appConfig) {

    @Volatile
    private var resolvedProxyAddress: InetSocketAddress? = null

    init {
        proxyType = when (proxy.type) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS5 -> Proxy.Type.SOCKS
        }
        // Left unresolved, so a failing lookup of the proxy's own name surfaces at the check, not at startup
        setProxyAddress(InetSocketAddress.createUnresolved(proxy.host, proxy.port))
        proxy.credentials?.let { credentials ->
            setProxyUsername(credentials.username)
            setProxyPassword(credentials.password)
        }
        setNumOfThreads(numOfThreads)
    }

    /**
     * Routes the connections the client opens from now on to [address], the current address of the proxy.
     */
    fun updateProxyAddress(address: InetAddress) {
        resolvedProxyAddress = InetSocketAddress(address, proxy.port)
    }

    /**
     * The client would resolve an unresolved proxy address itself, while it sets up a connection on its event loop,
     * so a slow lookup would hold up every other check of that client. It is resolved upfront instead, and handed
     * over through [updateProxyAddress].
     */
    override fun resolveProxy(isSsl: Boolean, host: String, port: Int): Proxy =
        resolvedProxyAddress?.let { Proxy(proxyType, it) } ?: super.resolveProxy(isSsl, host, port)
}

/**
 * Holds a separate HTTP client for every configured proxy.
 *
 * A single client with a `ProxySelector` can't be used instead: the selector only sees the target's host and port,
 * so it can't tell apart two monitors of the same target that use different routes, the connection pool of a client
 * is keyed by the target alone, so connections opened through one proxy would be reused for the others, and the proxy
 * credentials are configured once per client.
 *
 * The clients are built outside the bean context, because there is a single bean per configuration class, so each of
 * them runs its own event loop group, sized like the one of the direct client. They are only built for the proxies
 * that are actually used, on their first lookup.
 */
@Singleton
class ProxiedHttpClientRegistry(
    private val proxyRegistry: ProxyRegistry,
    private val hostnameResolver: BoundedHostnameResolver,
    private val applicationConfiguration: ApplicationConfiguration,
    private val appConfig: AppConfig,
    @Named(BaseHttpCheckerClientConfiguration.EVENT_LOOP_GROUP)
    private val eventLoopConfig: EventLoopGroupConfiguration,
    private val directConfiguration: HttpCheckerClientConfiguration,
) : AutoCloseable {

    private class ProxiedClient(val client: HttpClient, val configuration: ProxiedHttpCheckerClientConfiguration)

    private val clients = ConcurrentHashMap<String, ProxiedClient>()
    private val lookupTimeoutMs = TimeUnit.SECONDS.toMillis(appConfig.httpCheckTimeoutSeconds).toInt()

    /**
     * Returns the client of the proxy called [proxyName], with the proxy's address freshly resolved. It blocks on the
     * lookup, so it must not be called on an event loop.
     *
     * @return the client, or null if there is no such proxy.
     * @throws IOException if the proxy's name can't be resolved within the timeout of the HTTP checks.
     */
    fun clientFor(proxyName: String): HttpClient? {
        val proxy = proxyRegistry[proxyName] ?: return null
        val proxiedClient = clients.computeIfAbsent(proxyName) { createClient(proxy) }
        proxiedClient.configuration.updateProxyAddress(hostnameResolver.resolve(proxy.host, lookupTimeoutMs))
        return proxiedClient.client
    }

    private fun createClient(proxy: ConfiguredProxy): ProxiedClient {
        val configuration = ProxiedHttpCheckerClientConfiguration(
            config = applicationConfiguration,
            appConfig = appConfig,
            proxy = proxy,
            numOfThreads = eventLoopConfig.numThreads,
        )
        // A proxied check has to trust exactly the same certificates as a direct one
        configuration.sslConfiguration = directConfiguration.sslConfiguration
        return ProxiedClient(client = HttpClient.create(null, configuration), configuration = configuration)
    }

    @PreDestroy
    override fun close() {
        clients.values.forEach { it.client.close() }
    }
}
