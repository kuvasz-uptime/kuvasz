package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.testutils.TestDbContainer
import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

/**
 * Kotest project config for the `uiTest` source set (the `test` set's `ProjectConfig` lives on a separate classpath;
 * the shared helpers were extracted into `testFixtures` so the two don't collide). Boots the singleton Testcontainers
 * Postgres once for the suite and registers the Micronaut Kotest extension that powers `@MicronautTest`.
 */
object UiTestProjectConfig : AbstractProjectConfig() {

    override val extensions = listOf(MicronautKotest5Extension)

    override suspend fun beforeProject() {
        TestDbContainer.start()
    }

    override suspend fun afterProject() {
        TestDbContainer.stop()
    }
}
