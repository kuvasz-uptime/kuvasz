package com.akobor.kuvasz.utils

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.flywaydb.core.Flyway
import java.util.Base64

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

fun <T : Any> MutableHttpRequest<T>.addAuthenticationHeader(withValidCredentials: Boolean): MutableHttpRequest<T> {
    val credentials = if (withValidCredentials) "test-user:test-pass" else "bad-user:bad-pass"
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())
    return this.header("Authorization", "Basic $encodedCredentials")
}
