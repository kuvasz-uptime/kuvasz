package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.metrics.http.HttpMetricsExporter
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
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
                    "metrics-exports.ssl-expiry" to true
                )
                val ctx = shouldNotThrowAny { testAppContext(properties) }

                ctx.getBean<MeterRegistry>().shouldNotBeNull()
                ctx.getBeansOfType(HttpMetricsExporter::class.java) shouldHaveSize 4
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
                ctx.getBeansOfType(HttpMetricsExporter::class.java).shouldBeEmpty()
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
