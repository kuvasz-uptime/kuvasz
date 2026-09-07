plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.minify)

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

tasks.register<Exec>("jsTest") {
    group = "verification"
    description = "Runs the Node unit tests for the frontend JS helpers, requires Node.js on PATH."
    workingDir = project.file("src/jsTest")
    // The JUnit report is what CI parses into the job summary, the spec reporter keeps the console output readable
    val report = layout.buildDirectory.file("test-results/jsTest/TEST-jsTest.xml")
    doFirst { report.get().asFile.parentFile.mkdirs() }
    commandLine(
        "node",
        "--test",
        "--test-reporter=spec",
        "--test-reporter-destination=stdout",
        "--test-reporter=junit",
        "--test-reporter-destination=${report.get().asFile.absolutePath}",
    )
}
