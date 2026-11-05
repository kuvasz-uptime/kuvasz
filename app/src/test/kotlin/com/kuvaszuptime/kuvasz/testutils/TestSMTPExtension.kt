package com.kuvaszuptime.kuvasz.testutils

import com.kuvaszuptime.kuvasz.config.TransportStrategy
import io.kotest.core.extensions.ConstructorExtension
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

object TestSMTPExtension : TestListener, ConstructorExtension {

    const val MAILPIT_IMAGE = "axllent/mailpit:v1.27.11"
    const val MAILPIT_SMTP_PORT = 1025
    const val MAILPIT_HTTP_PORT = 8025

    private lateinit var testContainer: GenericContainer<*>
    private const val SMTP_CONFIG_HOST = "smtp-config.host"
    private const val SMTP_CONFIG_PORT = "smtp-config.port"
    private const val SMTP_CONFIG_TRANSPORT_STRATEGY = "smtp-config.transport-strategy"

    override fun <T : Spec> instantiate(clazz: KClass<T>): Spec? {
        clazz.findAnnotation<SMTPTest>()?.let { _ ->
            testContainer = KGenericContainer(MAILPIT_IMAGE).withExposedPorts(MAILPIT_SMTP_PORT, MAILPIT_HTTP_PORT)
            testContainer.start()

            System.setProperty(SMTP_CONFIG_HOST, testContainer.host)
            System.setProperty(SMTP_CONFIG_PORT, testContainer.getMappedPort(MAILPIT_SMTP_PORT).toString())
            System.setProperty(SMTP_CONFIG_TRANSPORT_STRATEGY, TransportStrategy.SMTP.name)
        }
        return null
    }

    override suspend fun afterSpec(spec: Spec) {
        if (::testContainer.isInitialized) {
            testContainer.stop()
            System.clearProperty(SMTP_CONFIG_HOST)
            System.clearProperty(SMTP_CONFIG_PORT)
            System.clearProperty(SMTP_CONFIG_TRANSPORT_STRATEGY)
        }
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class SMTPTest
