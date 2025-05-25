plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":shared"))
    compileOnly(libs.jooq.kotlin)

    implementation(libs.kotlinx.html.jvm)

    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(mn.kotest.assertions.core.jvm)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
