plugins {
    id("gradlebuild.internal.java")
    kotlin("jvm")
}

description = "Collection of test fixtures and tests for architecture testing Gradle code"

dependencies {
    api(platform(projects.distributionsDependencies))
    api(testLibs.archunit)
    api(testLibs.archunitJunit5Api)

    runtimeOnly(testLibs.archunitJunit5) {
        because("This is what we use to write our architecture tests")
    }
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
