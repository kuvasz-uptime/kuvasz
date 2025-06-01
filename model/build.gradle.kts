plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    id("io.micronaut.minimal.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("nu.studer.jooq")
}

dependencies {

    implementation(project(":shared"))
    compileOnly(libs.i18n4k)

    // Micronaut
    implementation(mn.micronaut.core)
    implementation(mn.micronaut.validation)
    implementation(mn.jackson.module.kotlin)
    implementation(mn.micronaut.http.client)

    // OpenAPI
    kapt(mn.micronaut.openapi)
    implementation(mn.swagger.annotations)

    // DB & jOOQ & Flyway
    implementation(libs.jooq.kotlin)
    implementation(libs.jooq.postgres.extensions)
    jooqGenerator(mn.postgresql)
    implementation(libs.jooq.gradle.plugin)

    // Testing
    testImplementation(mn.micronaut.test.kotest5)
    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(mn.kotest.assertions.core.jvm)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val localDbUrl: String by project
val localDbUser: String by project
val localDbPassword: String by project
val localDbSchema: String by project
val localDbDriver: String by project

jooq {
    val jooqVersion: String by project
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration.apply {
                jdbc.apply {
                    driver = localDbDriver
                    url = localDbUrl
                    user = localDbUser
                    password = localDbPassword
                }
                generator.apply {
                    database.apply {
                        inputSchema = localDbSchema
                        isOutputSchemaToDefault = false
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isValidationAnnotations = false
                        isFluentSetters = true
                        isPojos = true
                    }
                    target.apply {
                        directory = "src/jooq/java"
                        packageName = "com.kuvaszuptime.kuvasz.jooq"
                    }
                }
            }
        }
    }
}
