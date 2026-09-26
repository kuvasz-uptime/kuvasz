package com.kuvaszuptime.kuvasz.services.docker

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.ssl.DockerSslContextProvider
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * A byte stream to a Docker daemon. The two transports differ only in how the streams are obtained, so the HTTP
 * exchange on top of them is written once.
 */
internal class DockerConnection(
    val input: InputStream,
    val output: OutputStream,
    resource: Closeable,
) : Closeable by resource

@Singleton
@Requires(bean = DockerHostConfig::class)
class DockerConnectionFactory(private val sslContextProvider: DockerSslContextProvider) {

    private companion object {
        const val HTTPS_ENDPOINT_IDENTIFICATION = "HTTPS"
    }

    internal fun open(host: DockerHost, timeoutMs: Int): DockerConnection = when (val address = host.address) {
        // A unix domain socket needs no timeout on connect: it is local and either there or not. Like every other
        // blocking call here, it is bounded by the transport interrupting its worker, which closes the channel.
        is DockerDaemonAddress.UnixSocket -> SocketChannel
            .open(UnixDomainSocketAddress.of(address.path))
            .let { channel ->
                DockerConnection(
                    input = Channels.newInputStream(channel),
                    output = Channels.newOutputStream(channel),
                    resource = channel,
                )
            }

        is DockerDaemonAddress.Tcp -> openTcp(host, address, timeoutMs).let { socket ->
            DockerConnection(
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                resource = socket,
            )
        }
    }

    private fun openTcp(host: DockerHost, address: DockerDaemonAddress.Tcp, timeoutMs: Int): Socket {
        // Resolved before anything is opened, so that a failure here cannot strand a connected socket
        val sslContext = if (address.secure) sslContextProvider.forHost(host) else null

        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(address.host, address.port), timeoutMs)
            plain.soTimeout = timeoutMs
            return sslContext?.let { startTls(it, address, plain) } ?: plain
        } catch (ex: IOException) {
            // Nothing owns this socket until DockerConnection is built. A fatal TLS alert already closes it
            // through the wrapper's autoClose, but connect() and the wrapping itself can fail too.
            plain.closeQuietly()
            throw ex
        }
    }

    private fun startTls(sslContext: SSLContext, address: DockerDaemonAddress.Tcp, plain: Socket): SSLSocket {
        // Layering TLS over an already connected socket keeps the connect timeout while still handing the hostname to
        // the handshake, which endpoint identification needs in order to check it against the certificate.
        val socket = sslContext.socketFactory
            .createSocket(plain, address.host, address.port, true) as SSLSocket
        socket.sslParameters = socket.sslParameters.apply {
            endpointIdentificationAlgorithm = HTTPS_ENDPOINT_IDENTIFICATION
        }
        socket.startHandshake()

        return socket
    }

    private fun Closeable.closeQuietly() {
        runCatching { close() }
    }
}
