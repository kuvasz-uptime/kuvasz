package com.kuvaszuptime.kuvasz.database

import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
import javax.net.SocketFactory

/**
 * A pgjdbc socket factory connecting to PostgreSQL over a unix domain socket. pgjdbc instantiates it by its class name
 * from the `socketFactory` connection property, passing the path of the socket file (`socketFactoryArg`) to it, which
 * is null if it's not set. The host and port of the JDBC URL are ignored.
 */
class PostgresUnixSocketFactory(socketPath: String?) : SocketFactory() {

    private val socketPath: String = requireNotNull(socketPath) {
        "The socketFactoryArg connection property has to be set to the path of PostgreSQL's socket file"
    }

    override fun createSocket(): Socket = UnixDomainSocket(Path.of(socketPath))

    override fun createSocket(host: String?, port: Int): Socket = connectedSocket()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        connectedSocket()

    override fun createSocket(host: InetAddress?, port: Int): Socket = connectedSocket()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        connectedSocket()

    private fun connectedSocket(): Socket = createSocket().apply { connect(null) }
}
