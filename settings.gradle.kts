rootProject.name = "kuvasz"

pluginManagement {

    plugins {
        val kotlinVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion

        val micronautGradlePluginVersion = "3.7.5"
        id("io.micronaut.minimal.application") version micronautGradlePluginVersion
        id("io.micronaut.docker") version micronautGradlePluginVersion

        id("io.gitlab.arturbosch.detekt") version "1.22.0"
        id("com.google.cloud.tools.jib") version "3.3.1"
        id("nu.studer.jooq") version "8.1"
        id("com.palantir.git-version") version "3.0.0"
        id("com.github.ben-manes.versions") version "0.46.0"
        id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
        id("org.flywaydb.flyway") version "9.15.2"
    }
}
