import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.gitlab.arturbosch.detekt.Detekt

buildscript {
    val flywayPluginVersion: String by project
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:$flywayPluginVersion")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("io.micronaut.minimal.application")
    id("io.micronaut.docker")
    id("com.google.cloud.tools.jib")
    id("com.palantir.git-version")
    id("com.github.ben-manes.versions")
    id("org.flywaydb.flyway")
    id("com.gradleup.shadow")
    id("com.github.gmazzo.buildconfig")
}

val gitVersion: groovy.lang.Closure<String> by extra
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

micronaut {
    runtime("netty")
    testRuntime("kotest5")
    processing {
        incremental(true)
        annotations("com.kuvaszuptime.kuvasz.*")
    }
}

kapt {
    arguments {
        arg("micronaut.openapi.project.dir", projectDir.toString())
    }
}

dependencies {

    implementation(project(":model"))
    implementation(project(":shared"))
    implementation(project(":ui"))

    // Micronaut
    kapt(mn.micronaut.security.annotations)
    kapt(mn.micronaut.validation.processor)
    implementation(mn.jackson.module.kotlin)
    implementation(mn.jackson.dataformat.yaml)
    implementation(mn.jackson.datatype.jsr310)
    implementation(mn.micronaut.kotlin.runtime)
    implementation(mn.micronaut.jackson.databind)
    runtimeOnly(mn.snakeyaml)
    implementation(mn.micronaut.validation)
    implementation(mn.logback.classic)
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.rxjava3)
    implementation(mn.micronaut.retry)
    implementation(mn.micronaut.security.jwt)
    implementation(mn.micronaut.views.htmx)

    // OpenAPI
    kapt(mn.micronaut.openapi)
    implementation(mn.swagger.annotations)

    // DB & jOOQ & Flyway
    runtimeOnly(mn.flyway.postgresql)
    implementation(mn.micronaut.flyway)
    implementation(mn.micronaut.jdbc.hikari)
    implementation(mn.micronaut.jooq)
    implementation(libs.jooq.kotlin)
    implementation(libs.jooq.postgres.extensions)
    implementation(mn.postgresql)

    // Kotlin
    implementation(mn.micronaut.kotlin.extension.functions)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.arrow.core.data)

    // Mailer
    implementation(libs.simplejavamail)
    implementation(libs.simplejavamail.batchmodule)

    // i18n
    compileOnly(libs.i18n4k)
    testCompileOnly(libs.i18n4k)

    // Testing
    testImplementation(libs.mockk)
    testImplementation(mn.testcontainers.postgres)
    testImplementation(libs.mockserver.netty)
    detektPlugins(libs.detekt.formatting)
}

application {
    mainClass.set("com.kuvaszuptime.kuvasz.Application")
}

allOpen {
    annotation("io.micronaut.aop.Around")
    annotation("io.micronaut.http.annotation.Controller")
    annotation("jakarta.inject.Singleton")
}

tasks.withType<JavaExec> {
    jvmArgs(
        "-Xms64M",
        "-Xmx128M",
    )
    systemProperty("micronaut.config.files", file("../localdev/application-dev.yml"))
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}

tasks.withType<Detekt>().configureEach {
    exclude("/com/kuvaszuptime/kuvasz/buildconfig")
}

jib {
    from {
        image = "bellsoft/liberica-runtime-container:jre-21-cds-slim-musl"
        platforms {
            platform {
                os = "linux"
                architecture = "amd64"
            }
            platform {
                os = "linux"
                architecture = "arm64"
            }
        }
    }
    to {
        image = "kuvaszmonitoring/kuvasz:$version"
        tags = setOf("latest")
    }
    container {
        environment = mapOf(
            "JAVA_TOOL_OPTIONS" to "-Xms64M -Xmx128M",
            "MICRONAUT_CONFIG_FILES" to "/config/kuvasz.yml"
        )
    }
    extraDirectories {
        paths {
            path {
                setFrom("docker/bootstrap")
                into = "/"
            }
        }
    }
}

val updateApiDoc by tasks.registering(type = Copy::class) {
    dependsOn("kaptKotlin")
    from(layout.buildDirectory.file("tmp/kapt3/classes/main/META-INF/swagger/kuvasz-latest.yml"))
    into("$rootDir/docs/api-doc")
}

val localDbUrl: String by project
val localDbUser: String by project
val localDbPassword: String by project
val localDbSchema: String by project
val localDbDriver: String by project

flyway {
    cleanDisabled = false
    url = localDbUrl
    user = localDbUser
    password = localDbPassword
    schemas = arrayOf(localDbSchema)
    driver = localDbDriver
}

buildConfig {
    packageName("com.kuvaszuptime.kuvasz.buildconfig")
    buildConfigField("APP_VERSION", provider { gitVersion() })
}

// Importing the public resources (JS, CSS) from the UI module
tasks.processResources {
    dependsOn(":ui:jsMinify")
    from("$rootDir/ui/src/main/resources/public") {
        into("public")
    }
}
