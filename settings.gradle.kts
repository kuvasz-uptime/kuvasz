rootProject.name = "kuvasz"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("io.micronaut.platform.catalog") version "5.0.0"
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kuvasz"

include("app", "model", "shared", "ui")
