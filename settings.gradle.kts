rootProject.name = "kuvasz"

pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion

        val micronautGradlePluginVersion = "3.7.2"
        id("io.micronaut.minimal.application") version micronautGradlePluginVersion
        id("io.micronaut.docker") version micronautGradlePluginVersion

        id("io.gitlab.arturbosch.detekt") version "1.22.0"
        id("com.google.cloud.tools.jib") version "3.3.1"
        id("nu.studer.jooq") version "8.1"
        id("com.palantir.git-version") version "1.0.0"
        id("com.github.ben-manes.versions") version "0.45.0"
        id("org.jlleitschuh.gradle.ktlint") version "11.2.0"
        id("org.flywaydb.flyway") version "9.15.0"
    }
}