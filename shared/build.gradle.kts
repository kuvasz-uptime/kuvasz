import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("io.gitlab.arturbosch.detekt")
    id("de.comahe.i18n4k")
}

dependencies {
    implementation(libs.i18n4k)

    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(mn.kotest.assertions.core.jvm)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Detekt>().configureEach {
    exclude("/com/kuvaszuptime/kuvasz/i18n")
}
