plugins {
    id("gradlebuild.internal.java")
    kotlin("jvm")
}

description = "Asciidoctor extensions that only work with html backends"


dependencies {
    api(buildLibs.asciidoctor)
    api(buildLibs.asciidoctorApi)

    implementation(buildLibs.commonsIo)
    implementation(projects.docsAsciidoctorExtensionsBase)
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
