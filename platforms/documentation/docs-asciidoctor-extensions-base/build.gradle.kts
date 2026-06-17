plugins {
    id("gradlebuild.internal.java")
    groovy
    kotlin("jvm")
}

description = "Asciidoctor extensions that work with all backends"

dependencies {
    api(buildLibs.asciidoctor)
    api(buildLibs.asciidoctorApi)
    api(buildLibs.jspecify)

    implementation(buildLibs.commonsIo)
    testImplementation(testLibs.spock)
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
