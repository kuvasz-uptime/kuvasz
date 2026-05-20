import org.jooq.meta.kotlin.database
import org.jooq.meta.kotlin.forcedType
import org.jooq.meta.kotlin.forcedTypes
import org.jooq.meta.kotlin.generate
import org.jooq.meta.kotlin.generator
import org.jooq.meta.kotlin.jdbc
import org.jooq.meta.kotlin.matchers
import org.jooq.meta.kotlin.strategy
import org.jooq.meta.kotlin.table
import org.jooq.meta.kotlin.tables
import org.jooq.meta.kotlin.target

buildscript {
    val flywayPluginVersion: String by project
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:$flywayPluginVersion")
    }
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    id("io.micronaut.minimal.library")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("nu.studer.jooq")
    id("org.flywaydb.flyway")
    id("com.github.ben-manes.versions")
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
    implementation(libs.jooq.jackson.extensions)
    jooqGenerator(mn.postgresql)
    implementation(libs.jooq.gradle.plugin)
    runtimeOnly(mn.flyway.postgresql)

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

flyway {
    cleanDisabled = false
    url = localDbUrl
    user = localDbUser
    password = localDbPassword
    schemas = arrayOf(localDbSchema)
    driver = localDbDriver
}

jooq {
    val jooqVersion: String by project
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration {
                jdbc {
                    driver = localDbDriver
                    url = localDbUrl
                    user = localDbUser
                    password = localDbPassword
                }
                generator {
                    strategy {
                        matchers {
                            tables {
                                table {
                                    expression = "HTTP_UPTIME_EVENT"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord"
                                }
                                table {
                                    expression = "PUSH_UPTIME_EVENT"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord"
                                }
                                table {
                                    expression = "ICMP_UPTIME_EVENT"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord"
                                }
                                table {
                                    expression = "HTTP_MONITOR"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.MonitorRecord"
                                }
                                table {
                                    expression = "PUSH_MONITOR"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.MonitorRecord"
                                }
                                table {
                                    expression = "ICMP_MONITOR"
                                    recordImplements = "com.kuvaszuptime.kuvasz.jooq.MonitorRecord"
                                }
                            }
                        }
                    }
                    database {
                        inputSchema = localDbSchema
                        isOutputSchemaToDefault = false
                        excludes = "flyway_schema_history"

                        forcedTypes {
                            forcedType {
                                userType = "com.kuvaszuptime.kuvasz.models.handlers.IntegrationID[]"
                                converter = "com.kuvaszuptime.kuvasz.jooq.TextArrayToIntegrationIdArrayConverter"
                                isGenericConverter = false
                                includeExpression = "HTTP_MONITOR.INTEGRATIONS|PUSH_MONITOR.INTEGRATIONS|ICMP_MONITOR.INTEGRATIONS"
                            }
                            forcedType {
                                userType = "com.kuvaszuptime.kuvasz.models.monitor.MonitorID[]"
                                converter = "com.kuvaszuptime.kuvasz.jooq.TextArrayToMonitorIdArrayConverter"
                                isGenericConverter = false
                                includeExpression = "STATUS_PAGE.MONITORS"
                            }
                            forcedType {
                                userType = "com.fasterxml.jackson.databind.JsonNode"
                                isJsonConverter = true
                                includeExpression = "HTTP_MONITOR.REQUEST_HEADERS|HTTP_MONITOR.EXPECTED_HEADERS"
                            }
                        }
                    }
                    generate {
                        isDeprecated = false
                        isValidationAnnotations = false
                        isFluentSetters = true
                        isPojos = true
                    }
                    target {
                        directory = "src/jooq/java"
                        packageName = "com.kuvaszuptime.kuvasz.jooq"
                    }
                }
            }
        }
    }
}

val migrateAndGenerate by tasks.registering {
    dependsOn("flywayMigrate")
    dependsOn("generateJooq")
}
