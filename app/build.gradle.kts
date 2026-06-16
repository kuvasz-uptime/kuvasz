
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.micronaut.minimal.application)
    alias(libs.plugins.micronaut.docker)
    alias(libs.plugins.jib)
    alias(libs.plugins.git.version)

    alias(libs.plugins.shadow)
    alias(libs.plugins.buildconfig)
}

val gitVersion: groovy.lang.Closure<String> by extra
version = rootProject.version
val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
val details = versionDetails()

java {
    sourceCompatibility = JavaVersion.VERSION_25
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
        arg("micronaut.openapi.target.file", "../docs/docs/api-docs/kuvasz-latest.yml")
        arg("micronaut.openapi.additional.files", "src/main/resources/swagger")
    }
}

dependencies {

    implementation(project(":model"))
    implementation(project(":shared"))
    implementation(project(":ui"))

    // Micronaut
    kapt(mn.micronaut.security.annotations)
    kapt(mn.micronaut.validation.processor)
    implementation(mn.micronaut.mcp.server.java.sdk)
    implementation(mn.jackson.module.kotlin)
    implementation(mn.jackson.dataformat.yaml)
    implementation(mn.micronaut.kotlin.runtime)
    implementation(mn.micronaut.jackson.databind)
    runtimeOnly(mn.snakeyaml)
    implementation(mn.micronaut.validation)
    implementation(mn.logback.classic)
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.rxjava3)
    implementation(mn.micronaut.retry)
    implementation(mn.micronaut.security.jwt)
    implementation(mn.micronaut.security.oauth2)
    implementation(mn.micronaut.views.htmx)
    implementation(mn.micronaut.cache.core)
    implementation(mn.micronaut.cache.caffeine)

    // OpenAPI & JsonSchema
    kapt(mn.micronaut.openapi)
    compileOnly(mn.micronaut.openapi.annotations)
    implementation(mn.swagger.annotations)
    implementation(mn.micronaut.json.schema.annotations)
    kapt(mn.micronaut.json.schema.processor)

    // DB & jOOQ & Flyway
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

    // Mailer
    implementation(libs.simplejavamail)
    implementation(libs.simplejavamail.batchmodule)

    // i18n
    compileOnly(libs.i18n4k)
    testCompileOnly(libs.i18n4k)

    // Metrics exporting
    implementation(mn.micronaut.micrometer.core)
    implementation(mn.micrometer.core)
    implementation(mn.micronaut.micrometer.registry.prometheus)
    implementation(mn.micronaut.micrometer.registry.otlp)

    // Templating
    implementation(libs.pebble)

    // Testing
    kaptTest(mn.micronaut.inject.java)
    testImplementation(libs.mockk)
    testImplementation(mn.micronaut.test.kotest5)
    testImplementation(libs.kotest.data)
    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.pg)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.mockserver.netty)
    testImplementation(mn.micronaut.mcp.client.java.sdk)
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

tasks.withType<Test> {
    jvmArgs("-Xmx2048M")
}

tasks.withType<JavaExec> {
    jvmArgs(
        "-Xms64M",
        "-Xmx192M",
    )
    systemProperty("micronaut.environments", "macos") // TODO revisit
    systemProperty("micronaut.config.files", file("../localdev/application-dev.yml"))
}

tasks.withType<ShadowJar> {
    dependsOn("updateApiDoc")
    mergeServiceFiles()
}

tasks.withType<Detekt>().configureEach {
    exclude("/com/kuvaszuptime/kuvasz/buildconfig")
}

jib {
    from {
        image = "eclipse-temurin:25-jre-alpine-3.23"
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
        labels = mapOf(
            "org.opencontainers.image.source" to "https://github.com/kuvasz-uptime/kuvasz",
            "org.opencontainers.image.version" to version.toString(),
            "org.opencontainers.image.revision" to details.gitHash,
            "org.opencontainers.image.description" to "Kuvasz (pronounce as [ˈkuvɒs]) is an open-source uptime and SSL monitoring service, with multiple notification channels, status pages, IAC support via YAML, Prometheus integration, a complete REST API and many more",
        )
        environment = mapOf(
            "JAVA_TOOL_OPTIONS" to "-Xms64M -Xmx192M",
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

val updateApiDoc by tasks.registering {
    description = "Implicitly generates the OpenAPI docs (by depending on the kaptKotlin task)"
    dependsOn("kaptKotlin")
}

val validateJsonSchemas by tasks.registering {
    dependsOn("kaptKotlin")
    group = "Verification"
    description =
        "Checks that none of the generated JSON schema definitions contain a \"\$ref\" (external schema reference)."

    val schemasDir = layout.buildDirectory.dir("tmp/kapt3/classes/main/META-INF/schemas")

    doLast {
        val dir = schemasDir.get().asFile
        if (!dir.exists()) {
            throw GradleException("Schemas directory not found: ${dir.absolutePath}")
        }

        val violations = dir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filter { it.readText().contains("\"\$ref\"") }
            .toList()

        if (violations.isNotEmpty()) {
            val fileList = violations.joinToString("\n") { "  - ${it.name}" }
            throw GradleException(
                "The following JSON schema(s) contain a \"\$ref\" (external schema references are not allowed):\n$fileList"
            )
        }

        logger.lifecycle("✅ All JSON schemas are self-contained (no \"\$ref\" found).")
    }
}

// Generates the Git-based project version into the Kotlin code
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
