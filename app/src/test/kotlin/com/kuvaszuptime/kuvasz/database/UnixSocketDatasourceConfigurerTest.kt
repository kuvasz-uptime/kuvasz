package com.kuvaszuptime.kuvasz.database

import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration
import io.micronaut.context.env.PropertySource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.net.Socket
import java.net.URI
import java.nio.channels.Channels
import java.util.concurrent.CompletableFuture

private const val CUSTOM_PORT = 6543

// The URL of the Testcontainers database, which the relay forwards the unix socket connections to
private val databaseUri = URI(System.getProperty("datasources.default.url").removePrefix("jdbc:"))

/** Forwards every connection made to its socket file to the database over TCP, like socat would. */
private fun relayToDatabase(port: Int = 5432) = TestUnixSocketServer(port) { channel ->
    Socket(databaseUri.host, databaseUri.port).use { database ->
        val upstream = CompletableFuture.runAsync {
            runCatching { Channels.newInputStream(channel).transferTo(database.getOutputStream()) }
            runCatching { database.shutdownOutput() }
        }
        runCatching { database.getInputStream().transferTo(Channels.newOutputStream(channel)) }
        upstream.join()
    }
}

private fun jdbcUrl(host: String, port: Any) = "jdbc:postgresql://$host:$port${databaseUri.path}?${databaseUri.query}"

class UnixSocketDatasourceConfigurerTest : BehaviorSpec({

    given("DATABASE_HOST pointing to the directory of a unix domain socket, and a custom DATABASE_PORT") {
        val relay = autoClose(relayToDatabase(CUSTOM_PORT))
        val socketDirectory = relay.directory.toString()
        // Mapped the same way as the real environment variables, and built into the URL like in application.yml
        val environment = PropertySource.of(
            "environment-variables",
            mapOf(
                "DATABASE_HOST" to socketDirectory,
                "DATABASE_PORT" to CUSTOM_PORT,
                "DATASOURCES_DEFAULT_URL" to jdbcUrl("\${DATABASE_HOST}", "\${DATABASE_PORT}"),
            ),
            PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE,
        )
        val ctx = testAppContext(environment)

        `when`("the application starts") {
            val configuration = ctx.getBean<DatasourceConfiguration>()

            then("it should connect through the socket file named after the port, replacing the path in the URL") {
                ctx.getBean<DSLContext>().select(DSL.inline(1)).fetchOne(0) shouldBe 1
                relay.acceptedConnections.get() shouldBeGreaterThan 0

                configuration.url shouldBe jdbcUrl("localhost", CUSTOM_PORT)
                configuration.dataSourceProperties["socketFactory"] shouldBe
                    PostgresUnixSocketFactory::class.java.name
                configuration.dataSourceProperties["socketFactoryArg"] shouldBe
                    "$socketDirectory/.s.PGSQL.$CUSTOM_PORT"
            }
        }
    }

    given("the socket factory configured through the datasource properties in the YAML") {
        val relay = autoClose(relayToDatabase())
        val ctx = testAppContext(
            PropertySource.of(
                mapOf(
                    "datasources.default.url" to jdbcUrl("localhost", 5432),
                    "test.socket-file" to relay.path.toString(),
                )
            ),
            "unix-socket-datasource-properties",
        )

        `when`("the application starts") {

            then("it should connect through the socket, without the DATABASE_HOST based configuration") {
                ctx.containsBean(UnixSocketDatasourceConfigurer::class.java) shouldBe false
                ctx.getBean<DSLContext>().select(DSL.inline(1)).fetchOne(0) shouldBe 1
                relay.acceptedConnections.get() shouldBeGreaterThan 0
            }
        }
    }

    given("DATABASE_HOST being a hostname") {
        val ctx = testAppContext(PropertySource.of(mapOf(UnixSocketDatasourceConfigurer.HOST_PROPERTY to "localhost")))

        `when`("the application starts") {
            val configuration = ctx.getBean<DatasourceConfiguration>()

            then("it should connect over TCP, leaving the datasource untouched") {
                ctx.containsBean(UnixSocketDatasourceConfigurer::class.java) shouldBe false
                ctx.getBean<DSLContext>().select(DSL.inline(1)).fetchOne(0) shouldBe 1
                configuration.dataSourceProperties shouldNotContainKey "socketFactory"
            }
        }
    }
})
