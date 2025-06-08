package io.micronaut.test.kotest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import com.kuvaszuptime.kuvasz.testutils.TestSMTPExtension
import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

object ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(TestSMTPExtension, MicronautKotest5Extension)

    override suspend fun beforeProject() {
        TestDbContainer.start()
    }

    override suspend fun afterProject() {
        TestDbContainer.stop()
    }
}
