plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    named("main") { java.srcDirs("src/main/kotlin") }
    named("test") { java.srcDirs("src/test/kotlin") }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed", "skipped") }
}
