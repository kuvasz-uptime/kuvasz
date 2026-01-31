rootProject.name = "kuvasz"

pluginManagement {

    repositories {
        gradlePluginPortal()
    }

    plugins {
        val kotlinVersion: String by settings
        val jooqPluginVersion: String by settings
        val flywayPluginVersion: String by settings
        val detektVersion: String by settings
        val micronautGradlePluginVersion = "4.6.1"

        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion

        id("io.micronaut.minimal.application") version micronautGradlePluginVersion
        id("io.micronaut.docker") version micronautGradlePluginVersion
        id("io.micronaut.platform.catalog") version micronautGradlePluginVersion
        id("io.micronaut.minimal.library") version micronautGradlePluginVersion

        id("dev.detekt") version detektVersion
        id("com.google.cloud.tools.jib") version "3.5.2"
        id("nu.studer.jooq") version jooqPluginVersion
        id("com.palantir.git-version") version "4.3.0"
        id("com.github.ben-manes.versions") version "0.53.0"
        id("org.flywaydb.flyway") version flywayPluginVersion
        id("com.gradleup.shadow") version "9.3.1"
        id("com.github.gmazzo.buildconfig") version "6.0.7"
        id("org.gradlewebtools.minify") version "2.1.1"
        id("org.jetbrains.kotlinx.kover") version "0.9.5"
        id("de.comahe.i18n4k") version "0.11.1"
    }
}

plugins {
    id("io.micronaut.platform.catalog")
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            val kotlinVersion: String by settings
            val kotlinCoroutinesVersion: String by settings
            val jooqVersion: String by settings
            val jooqPluginVersion: String by settings
            val detektVersion: String by settings

            // Kotlin
            version("kotlin", kotlinVersion)
            version("kotlinCoroutines", kotlinCoroutinesVersion)
            library("kotlin-stdlib-jdk8", "org.jetbrains.kotlin", "kotlin-stdlib-jdk8").versionRef("kotlin")
            library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef("kotlin")
            library(
                "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-core"
            ).versionRef("kotlinCoroutines")
            library(
                "kotlinx-coroutines-reactive",
                "org.jetbrains.kotlinx",
                "kotlinx-coroutines-reactive"
            ).versionRef("kotlinCoroutines")

            // UI
            library("kotlinx-html-jvm", "org.jetbrains.kotlinx", "kotlinx-html-jvm").version("0.12.0")
            library("kotlin-htmx", "com.iodesystems.kotlin-htmx", "htmx").version("0.0.1")
            library("i18n4k", "de.comahe.i18n4k", "i18n4k-core-jvm").version("0.11.1")

            // jOOQ
            library("jooq-kotlin", "org.jooq", "jooq-kotlin").version(jooqVersion)
            library("jooq-postgres-extensions", "org.jooq", "jooq-postgres-extensions").version(jooqVersion)
            library("jooq-jackson-extensions", "org.jooq", "jooq-jackson-extensions").version(jooqVersion)
            library("jooq-gradle-plugin", "nu.studer", "gradle-jooq-plugin").version(jooqPluginVersion)

            // Simple Java Mail
            version("simpleJavaMail", "8.12.6")
            library("simplejavamail", "org.simplejavamail", "simple-java-mail").versionRef("simpleJavaMail")
            library("simplejavamail-batchmodule", "org.simplejavamail", "batch-module").versionRef("simpleJavaMail")

            // Tests
            library("mockk", "io.mockk", "mockk").version("1.14.9")
            library("mockserver-netty", "org.mock-server", "mockserver-netty").version("5.15.0")
            val testcontainersVersion = "2.0.3"
            library("testcontainers", "org.testcontainers", "testcontainers").version(testcontainersVersion)
            library("testcontainers-pg", "org.testcontainers", "testcontainers-postgresql").version(
                testcontainersVersion
            )

            // Misc
            library("arrow-core-data", "io.arrow-kt", "arrow-core-data").version("0.12.1")
            library(
                "detekt-formatting",
                "io.gitlab.arturbosch.detekt",
                "detekt-formatting"
            ).version("1.23.8")
        }
    }
}

rootProject.name = "kuvasz"

include("app", "model", "shared", "ui")
