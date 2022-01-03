package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.config.TransportStrategy
import org.testcontainers.containers.GenericContainer

class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

object TestMailhogContainer {
    private lateinit var instance: KGenericContainer
    private const val IMAGE_NAME = "mailhog/mailhog"
    private const val SMTP_PORT = 1025
    private const val HTTP_PORT = 8025

    fun start() {
        instance = KGenericContainer(IMAGE_NAME).withExposedPorts(SMTP_PORT, HTTP_PORT)
        instance.start()

        System.setProperty("smtp-config.host", instance.containerIpAddress)
        System.setProperty("smtp-config.port", instance.getMappedPort(SMTP_PORT).toString())
        System.setProperty("smtp-config.transport-strategy", TransportStrategy.SMTP.name)
    }

    fun stop() {
        instance.stop()
    }
}
