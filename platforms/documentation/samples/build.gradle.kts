plugins {
    id("gradlebuild.internal.java")
    kotlin("jvm")
}

description = "Integration tests for our documentation snippets (aka samples)"

dependencies {
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.processServices)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(testLibs.samplesCheck) {
        exclude(group = "org.codehaus.groovy", module = "groovy-all")
    }
    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.modelCore))
    integTestImplementation(testFixtures(projects.testingBase))

    integTestDistributionRuntimeOnly(projects.distributionsFull)
    implementation(kotlin("stdlib-jdk8"))
}

testFilesCleanup.reportOnly = true

errorprone {
    nullawayEnabled = true
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(17)
}
