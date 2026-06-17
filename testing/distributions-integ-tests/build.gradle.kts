import gradlebuild.basics.buildBranch
import gradlebuild.basics.buildCommitId

plugins {
    id("gradlebuild.internal.java")
    kotlin("jvm")
}

description = "The collector project for the 'integ-tests' portion of the Gradle distribution"

dependencies {
    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.coreApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.ant)

    integTestBinDistribution(projects.distributionsFull)
    integTestAllDistribution(projects.distributionsFull)
    integTestDocsDistribution(projects.distributionsFull)
    integTestSrcDistribution(projects.distributionsFull)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
    implementation(kotlin("stdlib-jdk8"))
}

// Using lazy makes sure we do not invalidate CC entries when head commit changes
// The hack is needed because Gradle does not support `Provider<?>` in systemProperty
// See https://github.com/gradle/gradle/issues/12247
tasks.forkingIntegTest {
    systemProperty("gradleBuildBranch", lazy(buildBranch::get))
    systemProperty("gradleBuildCommitId", lazy(buildCommitId::get))
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
