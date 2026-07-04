rootProject.name = "kuvasz"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("io.micronaut.platform.catalog") version "5.0.2"
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kuvasz"

include("app", "model", "shared", "ui")
