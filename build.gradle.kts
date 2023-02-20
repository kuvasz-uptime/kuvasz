import org.jooq.meta.jaxb.MatcherRule
import org.jooq.meta.jaxb.MatcherTransformType
import org.jooq.meta.jaxb.Matchers
import org.jooq.meta.jaxb.MatchersTableType

buildscript {
    val jooqVersion: String by project
    configurations["classpath"].resolutionStrategy.eachDependency {
        if (requested.group == "org.jooq") {
            useVersion(jooqVersion)
        }
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
    version("3.8.5")
    runtime("netty")
    testRuntime("kotest5")
    processing {
        incremental(true)
        annotations("com.kuvaszuptime.kuvasz.*")
    }
}

val kotlinCoroutinesVersion = "1.6.4"
val jooqPluginVersion: String by project
val kotlinVersion: String by project
val postgresVersion = "42.5.4"
val simpleJavaMailVersion = "7.8.2"

dependencies {
    kapt("io.micronaut.openapi:micronaut-openapi")
    kapt("io.micronaut.security:micronaut-security-annotations")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("nu.studer:gradle-jooq-plugin:$jooqPluginVersion")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("ch.qos.logback:logback-classic")
    implementation("io.arrow-kt:arrow-core-data:0.12.1")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client")
    implementation("io.micronaut.security:micronaut-security-jwt")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.sql:micronaut-jooq")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.swagger.core.v3:swagger-annotations")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.simplejavamail:batch-module:$simpleJavaMailVersion")
    implementation("org.simplejavamail:simple-java-mail:$simpleJavaMailVersion")
    jooqGenerator("org.postgresql:postgresql:$postgresVersion")
    testImplementation("io.mockk:mockk:1.13.3")
    testImplementation("org.testcontainers:postgresql:1.16.2")
}

application {
    mainClass.set("com.kuvaszuptime.kuvasz.ApplicationKt")
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

jib {
    from {
        image = "gcr.io/distroless/java17-debian11"
        platforms {
            platform {
                os = "linux"
                architecture = "amd64"
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
                        isOutputSchemaToDefault = true
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isValidationAnnotations = true
                        isJpaAnnotations = true
                        isPojos = true
                        isImmutablePojos = false
                        isFluentSetters = true
                        isDaos = true
                    }
                    target.apply {
                        directory = "src/jooq/java"
                        packageName = "com.kuvaszuptime.kuvasz"
                    }
                    strategy.apply {
                        name = "PojoSuffixStrategy"
                        matchers = Matchers().apply {
                            tables = listOf(
                                MatchersTableType().apply {
                                    pojoClass = MatcherRule().apply {
                                        transform = MatcherTransformType.PASCAL
                                        expression = "\$0_Pojo"
                                    }
                                }
                            )
                        }
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
