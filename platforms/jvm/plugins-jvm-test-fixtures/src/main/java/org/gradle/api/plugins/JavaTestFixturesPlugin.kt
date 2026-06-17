/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.internal.JavaPluginHelper.getDefaultTestSuite
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.internal.component.external.model.TestFixturesSupport
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
import javax.inject.Inject

/**
 * Adds support for producing test fixtures. This plugin will automatically
 * create a `testFixtures` source set, and wires the tests to use those
 * test fixtures automatically.
 *
 * Other projects may consume the test fixtures of the current project by
 * declaring a dependency using the [DependencyHandler.testFixtures]
 * method.
 *
 * This should really be named `JVMTestFixturesPlugin`, as there is no requirement
 * for the test fixtures to be written in Java, any supported JVM language will work.
 *
 * @since 5.6
 * @see [Java Test Fixtures reference](https://docs.gradle.org/current/userguide/java_testing.html.sec:java_test_fixtures)
 */
abstract class JavaTestFixturesPlugin @Inject constructor() : Plugin<Project?> {
    @get:Inject
    protected abstract val dependencyFactory: DependencyFactory?

    override fun apply(project: Project) {
        project.getPlugins().apply<JavaBasePlugin?>(JavaBasePlugin::class.java)
        val extension = project.getExtensions().getByType<JavaPluginExtension>(JavaPluginExtension::class.java)

        val testFixturesSourceSet: SourceSet = extension.sourceSets!!.maybeCreate(TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME)!!

        val feature: JvmFeatureInternal = DefaultJvmFeature(
            TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME,
            testFixturesSourceSet,
            mutableSetOf<Capability>(ProjectDerivedCapability(project as ProjectInternal, TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME)),
            project,
            false
        )

        feature.withApi()

        project.getPluginManager().withPlugin("java", Action { plugin: AppliedPlugin? ->
            val component = getJavaComponent(project) as DefaultJvmSoftwareComponent
            component.features.add(feature)

            component.addVariantsFromConfiguration(feature.apiElementsConfiguration, JavaConfigurationVariantMapping("compile", true, feature.compileClasspathConfiguration))
            component.addVariantsFromConfiguration(feature.runtimeElementsConfiguration, JavaConfigurationVariantMapping("runtime", true, feature.runtimeClasspathConfiguration))
            createImplicitTestFixturesDependencies(feature, project)
        })
    }

    private fun createImplicitTestFixturesDependencies(feature: JvmFeatureInternal, project: Project) {
        val dependencies = project.getDependencies()

        // Test fixtures depend on the project.
        feature.apiConfiguration.getDependencies().add(this.dependencyFactory.createProjectDependency())

        // The tests depend on the test fixtures.
        val testSourceSet = getDefaultTestSuite(project).sources
        val testImplementation = project.getConfigurations().getByName(testSourceSet.implementationConfigurationName!!)
        testImplementation.getDependencies().add(dependencies.testFixtures(this.dependencyFactory.createProjectDependency()))

        // Overwrite what the Java plugin defines for test, in order to avoid duplicate classes
        // see gradle/gradle#10872
        val configurations = project.getConfigurations()
        testSourceSet.compileClasspath = project.getObjects().fileCollection().from(configurations.getByName(testSourceSet.compileClasspathConfigurationName!!))
        testSourceSet.runtimeClasspath = project.getObjects().fileCollection().from(testSourceSet.output, configurations.getByName(testSourceSet.runtimeClasspathConfigurationName!!))
    }
}
