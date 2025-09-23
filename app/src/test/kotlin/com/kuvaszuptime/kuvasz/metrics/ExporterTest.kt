package com.kuvaszuptime.kuvasz.metrics

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micronaut.context.ApplicationContext
import org.jooq.DSLContext

abstract class ExporterTest(private val env: String, body: BehaviorSpec.() -> Unit = {}) : BehaviorSpec(body) {

    var appContext: ApplicationContext? = null

    fun getMonitorRepository() = appContext?.getBean<HttpMonitorRepository>().shouldNotBeNull()
    fun latencyLogRepository() = appContext?.getBean<HttpLatencyLogRepository>().shouldNotBeNull()
    fun meterRegistry() = appContext?.getBean<MeterRegistry>().shouldNotBeNull()
    fun eventDispatcher() = appContext?.getBean<EventDispatcher>().shouldNotBeNull()
    fun httpMonitorActions() = appContext?.getBean<HttpMonitorActions>().shouldNotBeNull()
    fun sslEventRepository() = appContext?.getBean<SSLEventRepository>().shouldNotBeNull()
    fun uptimeEventRepository() = appContext?.getBean<HttpUptimeEventRepository>().shouldNotBeNull()

    val monitorEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::enabled.name, true)
    val monitorDisableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::enabled.name, false)
    val monitorNameUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::name.name, "new-name")
    val monitorSSLEnableUpdate: ObjectNode =
        JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::sslCheckEnabled.name, true)

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        // Doing a final manual cleanup after each tests to make sure that we don't leave any data behind that would
        // influence the consecutive tests
        val ephemeralAppContext = testAppContext()
        ephemeralAppContext.getBean<DSLContext>().resetDatabase()
        ephemeralAppContext.stop()
        // Stopping the app context after each test, so we can practically simulate the app restart
        appContext?.stop()
        appContext = null
    }

    fun restartAppContextWithMetrics() {
        appContext.shouldNotBeNull().stop()
        appContext = testAppContext(env)
    }

    infix fun <T : Any> Meter.shouldHaveValue(expectedValue: T) {
        this.measure().firstOrNull()?.value?.shouldNotBeNull()?.shouldBe(expectedValue)
    }

    infix fun Meter.shouldHaveNameTag(expectedName: String) {
        this.id.tags.shouldContain(Tag.of("name", expectedName))
    }

    infix fun Meter.shouldHaveTargetTag(expectedUrl: String) {
        this.id.tags.shouldContain(Tag.of("target", expectedUrl))
    }
}
