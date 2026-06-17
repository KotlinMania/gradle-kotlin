plugins {
    id("gradlebuild.incubation-report-aggregation")
    kotlin("jvm")
}

description = "The project to aggregate incubation reports from all subprojects"

dependencies {
    reports(platform(projects.distributionsFull))
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}
