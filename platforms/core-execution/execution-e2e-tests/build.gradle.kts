plugins {
    id("gradlebuild.internal.java")
    kotlin("jvm")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestImplementation(projects.execution)
    integTestDistributionRuntimeOnly(projects.distributionsFull)
    implementation(kotlin("stdlib-jdk8"))
}

errorprone {
    nullawayEnabled = true
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(17)
}
