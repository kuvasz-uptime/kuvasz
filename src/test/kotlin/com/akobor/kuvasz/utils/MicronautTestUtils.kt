package com.akobor.kuvasz.utils

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import org.flywaydb.core.Flyway

fun startTestApplication(): EmbeddedServer =
    ApplicationContext
        .build()
        .build()
        .start()
        .getBean(EmbeddedServer::class.java)
        .start()

inline fun <reified T : Any> EmbeddedServer.getBean(): T = this.applicationContext.getBean(T::class.java)

fun EmbeddedServer.getLowLevelClient(): RxHttpClient =
    this.applicationContext.createBean(RxHttpClient::class.java, this.url)

fun EmbeddedServer.resetDatabase() =
    this.getBean<Flyway>().let { flyway ->
        flyway.clean()
        flyway.migrate()
    }

fun generateCredentials(valid: Boolean) =
    UsernamePasswordCredentials(
        "test-user",
        if (valid) "test-pass" else "bad-pass"
    )
