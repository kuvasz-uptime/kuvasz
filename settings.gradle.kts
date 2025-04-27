rootProject.name = "kuvasz"

pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        val jooqPluginVersion: String by settings
        val flywayPluginVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion

        val micronautGradlePluginVersion = "4.5.3"
        id("io.micronaut.minimal.application") version micronautGradlePluginVersion
        id("io.micronaut.docker") version micronautGradlePluginVersion

        id("io.gitlab.arturbosch.detekt") version "1.23.8"
        id("com.google.cloud.tools.jib") version "3.4.5"
        id("nu.studer.jooq") version jooqPluginVersion
        id("com.palantir.git-version") version "3.2.0"
        id("com.github.ben-manes.versions") version "0.52.0"
        id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
        id("org.flywaydb.flyway") version flywayPluginVersion
        id("com.gradleup.shadow") version "8.3.6"
    }
}

plugins {
    // This one should match the micronautGradlePluginVersion above
    id("io.micronaut.platform.catalog") version "4.5.3"
}
