/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.TasksFromDependentProjects
import org.gradle.api.internal.artifacts.configurations.TasksFromProjectDependencies
import org.gradle.api.internal.component.SoftwareComponentContainerInternal
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.testing.base.TestingExtension
import java.util.function.Supplier
import javax.inject.Inject

/**
 *
 * A [Plugin] which compiles and tests Java source, and assembles it into a JAR file.
 *
 * This plugin creates a built-in [test suite][JvmTestSuite] named `test` that represents the [Test] task for Java projects.
 *
 * @see [Java plugin reference](https://docs.gradle.org/current/userguide/java_plugin.html)
 *
 * @see [JVM test suite plugin reference](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html)
 */
abstract class JavaPlugin @Inject constructor() : Plugin<Project?> {
    override fun apply(project: Project) {
        check(!project.getPluginManager().hasPlugin("java-platform")) {
            "The \"java\" or \"java-library\" plugin cannot be applied together with the \"java-platform\" plugin. " +
                    "A project is either a platform or a library but cannot be both at the same time."
        }
        val projectInternal = project as ProjectInternal

        project.getPluginManager().apply(JavaBasePlugin::class.java)
        project.getPluginManager()
            .apply("org.gradle.jvm-test-suite") // TODO: change to reference plugin class by name after project dependency cycles untangled; this will affect ApplyPluginBuildOperationIntegrationTest (will have to remove id)

        val sourceSets: SourceSetContainer = project.getExtensions().getByType<JavaPluginExtension?>(JavaPluginExtension::class.java).getSourceSets()

        project.getComponents().registerBinding<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java, DefaultJvmSoftwareComponent::class.java)
        val javaComponent: JvmSoftwareComponentInternal = createJavaComponent(projectInternal, sourceSets)

        configurePublishing(project.getPlugins(), project.getExtensions(), javaComponent.mainFeature.sourceSet)

        // Set the 'java' component as the project's default.
        project.getConfigurations().named(Dependency.DEFAULT_CONFIGURATION).configure(Action { conf: Configuration? ->
            conf.this!!.extendsFrom(javaComponent.mainFeature.runtimeElementsConfiguration.get())
        })
        (project.getComponents() as SoftwareComponentContainerInternal).getMainComponent().convention(javaComponent)

        // Build the main jar when running `assemble`.
        whileDisabled(Runnable {
            project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getArtifacts()
                .addAllLater(
                    javaComponent.mainFeature.runtimeElementsConfiguration.map({ conf -> conf.getArtifacts() })
                )
        })

        configureTestTaskOrdering(project.getTasks())
        configureDiagnostics(project, javaComponent.mainFeature)
        configureBuild(project)
    }

    companion object {
        /**
         * The name of the task that processes resources.
         */
        val PROCESS_RESOURCES_TASK_NAME: String = JvmConstants.PROCESS_RESOURCES_TASK_NAME

        /**
         * The name of the lifecycle task which outcome is that all the classes of a component are generated.
         */
        val CLASSES_TASK_NAME: String = JvmConstants.CLASSES_TASK_NAME

        /**
         * The name of the task which compiles Java sources.
         */
        val COMPILE_JAVA_TASK_NAME: String = JvmConstants.COMPILE_JAVA_TASK_NAME

        /**
         * The name of the task which processes the test resources.
         */
        val PROCESS_TEST_RESOURCES_TASK_NAME: String = JvmConstants.PROCESS_TEST_RESOURCES_TASK_NAME

        /**
         * The name of the lifecycle task which outcome is that all test classes of a component are generated.
         */
        val TEST_CLASSES_TASK_NAME: String = JvmConstants.TEST_CLASSES_TASK_NAME

        /**
         * The name of the task which compiles the test Java sources.
         */
        val COMPILE_TEST_JAVA_TASK_NAME: String = JvmConstants.COMPILE_TEST_JAVA_TASK_NAME

        /**
         * The name of the task which triggers execution of tests.
         */
        val TEST_TASK_NAME: String = JvmConstants.TEST_TASK_NAME

        /**
         * The name of the task which generates the component main jar.
         */
        val JAR_TASK_NAME: String = JvmConstants.JAR_TASK_NAME

        /**
         * The name of the task which generates the component javadoc.
         */
        val JAVADOC_TASK_NAME: String = JvmConstants.JAVADOC_TASK_NAME

        /**
         * The name of the API configuration, where dependencies exported by a component at compile time should
         * be declared.
         *
         * @since 3.4
         */
        val API_CONFIGURATION_NAME: String = JvmConstants.API_CONFIGURATION_NAME

        /**
         * The name of the implementation configuration, where dependencies that are only used internally by
         * a component should be declared.
         *
         * @since 3.4
         */
        val IMPLEMENTATION_CONFIGURATION_NAME: String = JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME

        /**
         * The name of the configuration to define the API elements of a component.
         * That is, the dependencies which are required to compile against that component.
         *
         * @since 3.4
         */
        val API_ELEMENTS_CONFIGURATION_NAME: String = JvmConstants.API_ELEMENTS_CONFIGURATION_NAME

        /**
         * The name of the configuration that is used to declare dependencies which are only required to compile a component,
         * but not at runtime.
         */
        val COMPILE_ONLY_CONFIGURATION_NAME: String = JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME

        /**
         * The name of the configuration to define the API elements of a component that are required to compile a component,
         * but not at runtime.
         *
         * @since 6.7
         */
        val COMPILE_ONLY_API_CONFIGURATION_NAME: String = JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME

        /**
         * The name of the runtime only dependencies configuration, used to declare dependencies
         * that should only be found at runtime.
         *
         * @since 3.4
         */
        val RUNTIME_ONLY_CONFIGURATION_NAME: String = JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME

        /**
         * The name of the runtime classpath configuration, used by a component to query its own runtime classpath.
         *
         * @since 3.4
         */
        val RUNTIME_CLASSPATH_CONFIGURATION_NAME: String = JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME

        /**
         * The name of the runtime elements configuration, that should be used by consumers
         * to query the runtime dependencies of a component.
         *
         * @since 3.4
         */
        val RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME

        /**
         * The name of the javadoc elements configuration.
         *
         * @since 6.0
         */
        val JAVADOC_ELEMENTS_CONFIGURATION_NAME: String = JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME

        /**
         * The name of the sources elements configuration.
         *
         * @since 6.0
         */
        val SOURCES_ELEMENTS_CONFIGURATION_NAME: String = JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME

        /**
         * The name of the compile classpath configuration.
         *
         * @since 3.4
         */
        val COMPILE_CLASSPATH_CONFIGURATION_NAME: String = JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME

        /**
         * The name of the annotation processor configuration.
         *
         * @since 4.6
         */
        val ANNOTATION_PROCESSOR_CONFIGURATION_NAME: String = JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME

        /**
         * The name of the test implementation dependencies configuration.
         *
         * @since 3.4
         */
        val TEST_IMPLEMENTATION_CONFIGURATION_NAME: String = JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME

        /**
         * The name of the configuration that should be used to declare dependencies which are only required
         * to compile the tests, but not when running them.
         */
        val TEST_COMPILE_ONLY_CONFIGURATION_NAME: String = JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME

        /**
         * The name of the test runtime only dependencies configuration.
         *
         * @since 3.4
         */
        val TEST_RUNTIME_ONLY_CONFIGURATION_NAME: String = JvmConstants.TEST_RUNTIME_ONLY_CONFIGURATION_NAME

        /**
         * The name of the test compile classpath configuration.
         *
         * @since 3.4
         */
        val TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME: String = JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME

        /**
         * The name of the test annotation processor configuration.
         *
         * @since 4.6
         */
        val TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME: String = JvmConstants.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME

        /**
         * The name of the test runtime classpath configuration.
         *
         * @since 3.4
         */
        val TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME: String = JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME

        private fun createMainFeature(project: ProjectInternal, sourceSets: SourceSetContainer): JvmFeatureInternal {
            val sourceSet: SourceSet = sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)!!

            val feature: JvmFeatureInternal = DefaultJvmFeature(
                JvmConstants.JAVA_MAIN_FEATURE_NAME,
                sourceSet,
                mutableSetOf<Capability?>(),
                project,
                false
            )

            // Create a source directories variant for the feature
            feature.withSourceElements()

            return feature
        }

        private fun createJavaComponent(project: ProjectInternal, sourceSets: SourceSetContainer): JvmSoftwareComponentInternal {
            return project.getComponents()
                .register<JvmSoftwareComponentInternal>(JvmConstants.JAVA_MAIN_COMPONENT_NAME, JvmSoftwareComponentInternal::class.java, Action { component: JvmSoftwareComponentInternal ->
                    // Create the main feature
                    val mainFeature: JvmFeatureInternal = createMainFeature(project, sourceSets)
                    component.features.add(mainFeature)

                    // TODO: This process of manually adding variants to the component should be handled automatically when adding the feature to the component.
                    (component as DefaultJvmSoftwareComponent).addVariantsFromConfiguration(
                        mainFeature.apiElementsConfiguration,
                        JavaConfigurationVariantMapping("compile", false, mainFeature.compileClasspathConfiguration)
                    )
                    component.addVariantsFromConfiguration(mainFeature.runtimeElementsConfiguration, JavaConfigurationVariantMapping("runtime", false, mainFeature.runtimeClasspathConfiguration))

                    // Create the default test suite
                    val defaultTestSuite: JvmTestSuite = createDefaultTestSuite(mainFeature, project.getConfigurations(), project.getTasks(), project.getExtensions(), project.getObjects())
                    component.testSuites.add(defaultTestSuite)
                }).get()
        }

        // TODO: This approach is not necessarily correct for non-main features. All publications will attempt to use the main feature's
        // compile and runtime classpaths for version mapping, even if a non-main feature is being published.
        private fun configurePublishing(plugins: PluginContainer, extensions: ExtensionContainer, sourceSet: SourceSet) {
            plugins.withType<PublishingPlugin?>(PublishingPlugin::class.java, Action { plugin: PublishingPlugin? ->
                val publishing = extensions.getByType<PublishingExtension>(PublishingExtension::class.java)
                // Set up the default configurations used when mapping to resolved versions
                publishing.getPublications().withType<IvyPublication?>(IvyPublication::class.java, Action { publication: IvyPublication? ->
                    val strategy = (publication as PublicationInternal<*>).getVersionMappingStrategy()
                    strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.compileClasspathConfigurationName!!)
                    strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.runtimeClasspathConfigurationName!!)
                })
                publishing.getPublications().withType<MavenPublication?>(MavenPublication::class.java, Action { publication: MavenPublication? ->
                    val strategy = (publication as PublicationInternal<*>).getVersionMappingStrategy()
                    strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.compileClasspathConfigurationName!!)
                    strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.runtimeClasspathConfigurationName!!)
                })
            })
        }

        /**
         * Unless there are other concerns, we'd prefer to run jar tasks prior to test tasks, as this might offer a small performance improvement
         * for common usage.  In practice, running test tasks tends to take longer than building a jar; especially as a project matures. If tasks
         * in downstream projects require the jar from this project, and the jar and test tasks in this project are available to be run in either order,
         * running jar first so that other projects can continue executing tasks in parallel while this project runs its tests could be an improvement.
         * However, while we want to prioritize cross-project dependencies to maximize parallelism if possible, we don't want to add an explicit
         * dependsOn() relationship between the jar task and the test task, so that any projects which need to run test tasks first will not need modification.
         */
        private fun configureTestTaskOrdering(tasks: TaskContainer) {
            val jarTasks = tasks.withType<Jar?>(Jar::class.java)
            tasks.withType<Test?>(Test::class.java).configureEach(Action { test: Test? -> test!!.shouldRunAfter(jarTasks) })
        }

        private fun createDefaultTestSuite(
            mainFeature: JvmFeatureInternal,
            configurations: ConfigurationContainer,
            tasks: TaskContainer,
            extensions: ExtensionContainer,
            objectFactory: ObjectFactory
        ): JvmTestSuite {
            val testing: TestingExtension? = extensions.findByType<TestingExtension?>(TestingExtension::class.java)
            val testSuite: NamedDomainObjectProvider<JvmTestSuite> =
                testing!!.getSuites().register<JvmTestSuite?>(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME, JvmTestSuite::class.java, Action { suite: JvmTestSuite? ->
                    val testSourceSet = suite!!.sources
                    val testImplementationConfiguration = configurations.getByName(testSourceSet.implementationConfigurationName!!)
                    val testRuntimeOnlyConfiguration = configurations.getByName(testSourceSet.runtimeOnlyConfigurationName!!)
                    val testCompileClasspathConfiguration = configurations.getByName(testSourceSet.compileClasspathConfigurationName!!)
                    val testRuntimeClasspathConfiguration = configurations.getByName(testSourceSet.runtimeClasspathConfigurationName!!)

                    // We cannot reference the main source set lazily (via a callable) since the IntelliJ model builder
                    // relies on the main source set being created before the tests. So, this code here cannot live in the
                    // JvmTestSuitePlugin and must live here, so that we can ensure we register this test suite after we've
                    // created the main source set.
                    val mainSourceSet: SourceSet = mainFeature.sourceSet
                    val mainSourceSetOutput: FileCollection = mainSourceSet.output!!
                    val testSourceSetOutput: FileCollection = testSourceSet.output!!
                    testSourceSet.compileClasspath = objectFactory.fileCollection().from(mainSourceSetOutput, testCompileClasspathConfiguration)
                    testSourceSet.runtimeClasspath = objectFactory.fileCollection().from(testSourceSetOutput, mainSourceSetOutput, testRuntimeClasspathConfiguration)

                    testImplementationConfiguration.extendsFrom(configurations.getByName(mainSourceSet.implementationConfigurationName!!))
                    testRuntimeOnlyConfiguration.extendsFrom(configurations.getByName(mainSourceSet.runtimeOnlyConfigurationName!!))
                })

            // Force the realization of this test suite, targets and task
            val suite = testSuite.get()

            tasks.named(JavaBasePlugin.CHECK_TASK_NAME, Action { task: Task? -> task!!.dependsOn(testSuite) })

            return suite
        }

        private fun configureDiagnostics(project: Project, mainFeature: JvmFeatureInternal) {
            project.getTasks().withType<DependencyInsightReportTask?>(DependencyInsightReportTask::class.java).configureEach(Action { task: DependencyInsightReportTask? ->
                DslObject(task!!).getConventionMapping().map("configuration", mainFeature::getCompileClasspathConfiguration)
            })
        }

        @Suppress("deprecation")
        private fun configureBuild(project: Project) {
            project.getTasks().named(JavaBasePlugin.BUILD_NEEDED_TASK_NAME, Action { task: Task? -> task!!.dependsOn(Companion.buildNeededTaskDependency(task)) }
            )
            project.getTasks().named(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, Action { task: Task? -> task!!.dependsOn(Companion.buildDependentsTaskDependency(task)) }
            )
        }

        @Deprecated("")
        private fun buildDependentsTaskDependency(task: Task): TaskDependency {
            val project = task.getProject() as ProjectInternal
            val configuration = project.getConfigurations().getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            return TasksFromDependentProjects(
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME,
                configuration.getName(),
                project.getServices().get<TaskDependencyFactory?>(TaskDependencyFactory::class.java)!!
            )
        }

        @Deprecated("")
        private fun buildNeededTaskDependency(task: Task): TaskDependency {
            val project = task.getProject() as ProjectInternal
            val configuration = project.getConfigurations().getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            return TasksFromProjectDependencies(
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME,
                Supplier { configuration.getAllDependencies().withType<ProjectDependency?>(ProjectDependency::class.java) },
                project.getServices().get<TaskDependencyFactory?>(TaskDependencyFactory::class.java)!!,
                project.getServices().get<ProjectStateRegistry?>(ProjectStateRegistry::class.java)!!
            )
        }
    }
}
