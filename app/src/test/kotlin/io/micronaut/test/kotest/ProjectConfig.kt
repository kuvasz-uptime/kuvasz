package io.micronaut.test.kotest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import com.kuvaszuptime.kuvasz.testutils.TestMailhogContainer
import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

object ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(MicronautKotest5Extension)

    override suspend fun beforeProject() {
        TestDbContainer.start()
        TestMailhogContainer.start()
    }

    override suspend fun afterProject() {
        TestDbContainer.stop()
        TestMailhogContainer.stop()
    }
}
