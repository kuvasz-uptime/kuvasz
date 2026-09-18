package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.config.ConnectivityCheckConfig
import com.kuvaszuptime.kuvasz.metrics.MetricsExporter
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityCheckScheduler
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityChecker
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException

class AppStartupTest : BehaviorSpec({

    given("the application") {

        `when`("no explicit metrics exporter config is specified") {

            then("it should start without errors") {

                val ctx = shouldNotThrowAny { testAppContext() }
                shouldThrow<NoSuchBeanException> { ctx.getBean<MeterRegistry>() }
            }
        }

        `when`("an explicit metrics exporter config is specified") {

            then("it should start without errors and the enabled exporters should be registered") {

                val properties = mapOf(
                    "micronaut.metrics.enabled" to true,
                    "micronaut.metrics.export.prometheus.enabled" to true,
                    "metrics-exports.http-uptime-status" to true,
                    "metrics-exports.http-latest-latency" to true,
                    "metrics-exports.ssl-status" to true,
                    "metrics-exports.ssl-expiry" to true,
                    "metrics-exports.push-uptime-status" to true,
                )
                val ctx = shouldNotThrowAny { testAppContext(properties) }

                ctx.getBean<MeterRegistry>().shouldNotBeNull()
                ctx.getBeansOfType(MetricsExporter::class.java) shouldHaveSize 5
            }
        }

        `when`("metrics are globally disabled but some exporters are enabled") {

            then("it should start without errors and no exporters should be registered") {

                val properties = mapOf(
                    "micronaut.metrics.enabled" to false,
                    "micronaut.metrics.export.prometheus.enabled" to true,
                    "metrics-exports.http-uptime-status" to true,
                    "metrics-exports.http-latest-latency" to true,
                    "metrics-exports.ssl-status" to true,
                    "metrics-exports.ssl-expiry" to true
                )
                val ctx = shouldNotThrowAny {
                    testAppContext(properties)
                }

                shouldThrow<NoSuchBeanException> { ctx.getBean<MeterRegistry>() }
                ctx.getBeansOfType(MetricsExporter::class.java).shouldBeEmpty()
            }
        }

        `when`("the connectivity check is not configured") {

            then("neither its config, its checker nor its scheduler is present, so nothing is ever dialed") {

                val ctx = shouldNotThrowAny { testAppContext() }

                shouldThrow<NoSuchBeanException> { ctx.getBean<ConnectivityCheckConfig>() }
                shouldThrow<NoSuchBeanException> { ctx.getBean<ConnectivityChecker>() }
                shouldThrow<NoSuchBeanException> { ctx.getBean<ConnectivityCheckScheduler>() }
            }
        }

        `when`("the connectivity check is enabled") {

            then("its scheduler is provisioned and the state is primed during the bootstrap") {

                val properties = mapOf(
                    "app-config.connectivity-check.enabled" to true,
                    // A port nothing listens on, so the probe fails instantly instead of reaching out to the network
                    "app-config.connectivity-check.targets" to listOf("127.0.0.1:1"),
                    "app-config.connectivity-check.interval-seconds" to 3600,
                )
                val ctx = shouldNotThrowAny { testAppContext(properties) }

                ctx.getBean<ConnectivityCheckScheduler>().shouldNotBeNull()
                ctx.getBean<ConnectivityChecker>().getStatus().state shouldBe ConnectivityState.DOWN
            }
        }
    }
})

fun TestConfiguration.testAppContext(vararg environments: String): ApplicationContext =
    autoClose(ApplicationContext.run("test", *environments))

fun TestConfiguration.testAppContext(properties: PropertySource, vararg environments: String): ApplicationContext =
    autoClose(ApplicationContext.run(properties, "test", *environments))

fun TestConfiguration.testAppContext(properties: Map<String, Any>, vararg environments: String): ApplicationContext =
    autoClose(ApplicationContext.run(properties, "test", *environments))
