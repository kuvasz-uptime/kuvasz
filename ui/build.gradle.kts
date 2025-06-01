plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
    id("io.gitlab.arturbosch.detekt")
    id("org.gradlewebtools.minify")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":shared"))

    compileOnly(libs.jooq.kotlin)
    compileOnly(libs.i18n4k)

    implementation(libs.kotlinx.html.jvm)
    implementation(libs.kotlin.htmx)
    implementation(mn.jackson.core)
    implementation(mn.jackson.module.kotlin)
    implementation(mn.jackson.datatype.jsr310)

    testImplementation(mn.kotest.runner.junit5.jvm)
    testImplementation(mn.kotest.assertions.core.jvm)
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

minification {
    js {
        srcDir = project.file("src/main/resources/js")
        dstDir = project.file("src/main/resources/public/dist/js")
    }
}

tasks.processResources {
    dependsOn(tasks.named("jsMinify"))
}
