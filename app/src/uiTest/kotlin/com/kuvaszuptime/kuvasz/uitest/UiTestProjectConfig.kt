package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

object UiTestProjectConfig : AbstractProjectConfig() {

    override val extensions = listOf(MicronautKotest5Extension)

    override suspend fun beforeProject() {
        TestDbContainer.start()
    }

    override suspend fun afterProject() {
        TestDbContainer.stop()
    }
}
