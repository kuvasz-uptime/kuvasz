package com.kuvaszuptime.kuvasz.database

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
import java.nio.file.Path

/**
 * Connects to PostgreSQL over its unix domain socket if `DATABASE_HOST` is a path, following libpq's convention that a
 * host starting with a slash is the directory of the socket, whose file is named after the port.
 *
 * pgjdbc can't parse a path as a host, so it's swapped for a placeholder in the JDBC URL, which the socket factory
 * ignores anyway.
 */
@Singleton
@Requires(property = UnixSocketDatasourceConfigurer.HOST_PROPERTY, pattern = "/.*")
class UnixSocketDatasourceConfigurer(
    @Property(name = HOST_PROPERTY) private val socketDirectory: String,
    @Property(name = PORT_PROPERTY, defaultValue = DEFAULT_PORT) private val port: Int,
) : BeanCreatedEventListener<DatasourceConfiguration> {

    companion object {
        const val HOST_PROPERTY = "database.host"
        const val PORT_PROPERTY = "database.port"
        private const val DEFAULT_PORT = "5432"
        private const val PLACEHOLDER_HOST = "localhost"
    }

    override fun onCreated(event: BeanCreatedEvent<DatasourceConfiguration>): DatasourceConfiguration =
        event.bean.apply {
            url = url.replace("//$socketDirectory:", "//$PLACEHOLDER_HOST:")
            addDataSourceProperty("socketFactory", PostgresUnixSocketFactory::class.java.name)
            addDataSourceProperty("socketFactoryArg", Path.of(socketDirectory, ".s.PGSQL.$port").toString())
        }
}
