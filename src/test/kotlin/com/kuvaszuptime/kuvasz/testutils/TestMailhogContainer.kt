package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.config.TransportStrategy
import org.testcontainers.containers.GenericContainer

class TestMailhogContainer : GenericContainer<TestMailhogContainer>("mailhog/mailhog") {
    companion object {
        lateinit var instance: TestMailhogContainer
        const val SMTP_PORT = 1025
        const val HTTP_PORT = 8025

        fun start() {
            if (!Companion::instance.isInitialized) {
                instance = TestMailhogContainer().withExposedPorts(SMTP_PORT, HTTP_PORT)
                instance.start()

                System.setProperty("smtp-config.host", instance.containerIpAddress)
                System.setProperty("smtp-config.port", instance.getMappedPort(SMTP_PORT).toString())
                System.setProperty("smtp-config.transport-strategy", TransportStrategy.SMTP.name)
            }
        }

        fun stop() {
            instance.stop()
        }
    }
}
