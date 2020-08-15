package io.micronaut.test.kotest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import com.kuvaszuptime.kuvasz.testutils.TestMailhogContainer
import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest.MicronautKotestExtension

object ProjectConfig : AbstractProjectConfig() {
    override fun listeners() = listOf(MicronautKotestExtension)
    override fun extensions() = listOf(MicronautKotestExtension)

    override fun beforeAll() {
        TestDbContainer.start()
        TestMailhogContainer.start()
    }

    override fun afterAll() {
        TestDbContainer.stop()
        TestMailhogContainer.stop()
    }
}
