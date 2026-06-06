package com.kuvaszuptime.kuvasz.models

import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(MicronautKotest5Extension)
}
