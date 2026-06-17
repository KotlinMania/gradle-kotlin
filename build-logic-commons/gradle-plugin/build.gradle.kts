plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

val kotlinDslPluginsVersion = providers
    .gradleProperty("kotlinDslPluginVersion")
    .orElse("6.7.3")
    .get()

dependencies {
    compileOnly(buildLibs.develocityPlugin)

    api(platform(projects.buildPlatform))

    implementation("gradlebuild:build-platform-plugin")
    implementation(projects.basics)
    implementation(projects.moduleIdentity)

    implementation(buildLibs.errorPronePlugin)
    implementation(buildLibs.nullawayPlugin)
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$kotlinDslPluginsVersion")
    implementation(buildLibs.kgp)
    implementation(buildLibs.testRetryPlugin)
    implementation(buildLibs.detektPlugin) {
        exclude(group = "org.gradle.experimental", module = "gradle-public-api")
    }
}
