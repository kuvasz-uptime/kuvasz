package com.kuvaszuptime.kuvasz.testutils

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer

fun startTestApplication(mockBeans: List<Any> = emptyList(), withRealAuth: Boolean = false): EmbeddedServer =
    ApplicationContext
        .build()
        .apply {
            if (withRealAuth) {
                properties(mapOf("micronaut.security.enabled" to "true"))
            }
        }
        .build()
        .apply {
            mockBeans.forEach { registerSingleton(it) }
        }
        .start()
        .getBean(EmbeddedServer::class.java)
        .start()

inline fun <reified T : Any> EmbeddedServer.getBean(): T = this.applicationContext.getBean(T::class.java)

fun EmbeddedServer.getLowLevelClient(): RxHttpClient =
    this.applicationContext.createBean(RxHttpClient::class.java, this.url)
