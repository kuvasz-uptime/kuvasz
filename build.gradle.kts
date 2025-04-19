import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

buildscript {
    val jooqVersion: String by project
    configurations["classpath"].resolutionStrategy.eachDependency {
        if (requested.group == "org.jooq") {
            useVersion(jooqVersion)
        }
    }
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
    id("jacoco")
    id("io.micronaut.minimal.application")
    id("io.micronaut.docker")
    id("com.google.cloud.tools.jib")
    id("nu.studer.jooq")
    id("com.palantir.git-version")
    id("com.github.ben-manes.versions")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.flywaydb.flyway")
    id("com.gradleup.shadow")
}

val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
group = "com.kuvaszuptime.kuvasz"
val javaTargetVersion = "17"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

micronaut {
    runtime("netty")
    testRuntime("kotest5")
    processing {
        incremental(true)
        annotations("com.kuvaszuptime.kuvasz.*")
    }
}

val jooqPluginVersion: String by project
val simpleJavaMailVersion = "8.12.5"

dependencies {

    // Micronaut
    kapt(mn.micronaut.security.annotations)
    kapt(mn.micronaut.validation.processor)
    runtimeOnly(mn.jackson.module.kotlin)
    runtimeOnly(mn.snakeyaml)
    implementation(mn.micronaut.validation)
    implementation(mn.logback.classic)
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.rxjava3)
    implementation(mn.micronaut.retry)
    implementation(mn.micronaut.security.jwt)

    // OpenAPI
    kapt(mn.micronaut.openapi)
    implementation(mn.swagger.annotations)

    // DB & jOOQ & Flyway
    runtimeOnly(mn.flyway.postgresql)
    implementation(mn.micronaut.flyway)
    implementation(mn.micronaut.jdbc.hikari)
    implementation(mn.micronaut.jooq)
    implementation(mn.postgresql)
    jooqGenerator(mn.postgresql)
    implementation("nu.studer:gradle-jooq-plugin:$jooqPluginVersion")

    // Kotlin
    implementation(mn.kotlin.stdlib.jdk8)
    implementation(mn.kotlin.reflect)
    implementation(mn.kotlinx.coroutines.core)
    implementation(mn.kotlinx.coroutines.reactive)
    implementation(mn.micronaut.kotlin.extension.functions)
    implementation("io.arrow-kt:arrow-core-data:0.12.1")

    // Mailer
    implementation("org.simplejavamail:batch-module:$simpleJavaMailVersion")
    implementation("org.simplejavamail:simple-java-mail:$simpleJavaMailVersion")

    // Testing
    testImplementation(mn.mockk)
    testImplementation(mn.testcontainers.postgres)
}

application {
    mainClass.set("com.kuvaszuptime.kuvasz.Application")
}

jacoco {
    toolVersion = "0.8.8"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(File("$buildDir/reports/jacoco/report.xml"))
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        fileTree("build/classes/kotlin/main") {
            exclude("com/kuvaszuptime/kuvasz/Application.class")
        }
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        fileTree("build/classes/kotlin/main") {
            exclude("com/kuvaszuptime/kuvasz/Application.class")
        }
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = BigDecimal.valueOf(0.9)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("detektMain")
    dependsOn("detektTest")
    dependsOn("jacocoTestCoverageVerification")
}

detekt {
    source = files(
        "src/main/kotlin",
        "src/test/kotlin"
    )
}

ktlint {
    version.set("0.43.2")
    disabledRules.set(setOf("no-wildcard-imports"))
}

tasks.named("build") {
    dependsOn("detekt")
}

tasks.withType<Test> {
    systemProperty("micronaut.environments", "test")
    finalizedBy("jacocoTestReport")
}

extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>()?.apply {
    jvmToolchain {
        this.languageVersion.set(JavaLanguageVersion.of(javaTargetVersion))
    }
}

allOpen {
    annotation("jakarta.inject.Singleton")
}

tasks.withType<JavaExec> {
    jvmArgs(
        "-Xms64M",
        "-Xmx128M",
        "-Dlogback.configurationFile=logback-dev.xml"
    )
    systemProperty("micronaut.environments", "dev")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}

jib {
    from {
        image = "bellsoft/liberica-runtime-container:jre-17-cds-slim-musl"
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
        environment = mapOf("JAVA_TOOL_OPTIONS" to "-Xms64M -Xmx128M")
    }
}

val updateApiDoc by tasks.registering(type = Copy::class) {
    dependsOn("kaptKotlin")
    from("$buildDir/tmp/kapt3/classes/main/META-INF/swagger/kuvasz-latest.yml")
    into("$projectDir/docs/api-doc")
}

val dbUrl = "jdbc:postgresql://localhost:12348/postgres"
val dbUser = "postgres"
val dbPassword = System.getenv("DB_PASSWORD") ?: "postgres"
val dbSchema = "kuvasz"
val dbDriver = "org.postgresql.Driver"

jooq {
    val jooqVersion: String by project
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration.apply {
                jdbc.apply {
                    driver = dbDriver
                    url = dbUrl
                    user = dbUser
                    password = dbPassword
                }
                generator.apply {
                    database.apply {
                        inputSchema = dbSchema
                        isOutputSchemaToDefault = false
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isValidationAnnotations = false
                        isFluentSetters = true
                    }
                    target.apply {
                        directory = "src/jooq/java"
                        packageName = "com.kuvaszuptime.kuvasz"
                    }
                }
            }
        }
    }
}

flyway {
    cleanDisabled = false
    url = dbUrl
    user = dbUser
    password = dbPassword
    schemas = arrayOf(dbSchema)
    driver = dbDriver
}
