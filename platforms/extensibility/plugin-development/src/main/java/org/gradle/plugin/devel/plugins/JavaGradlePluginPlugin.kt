/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugin.devel.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.plugins.PluginDescriptor
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.initialization.buildsrc.GradlePluginApiVersionAttributeConfigurationAction
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.buildoption.InternalOption
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.internal.jvm.JpmsConfiguration
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.devel.tasks.GeneratePluginDescriptors
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.use.resolve.internal.local.PluginPublication
import org.gradle.process.CommandLineArgumentProvider
import org.jspecify.annotations.NullMarked
import java.io.File
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.Callable

/**
 * A plugin for building java gradle plugins. Automatically generates plugin descriptors. Emits warnings for common error conditions.
 *
 * Provides a direct integration with TestKit by declaring the
 * `gradleTestKit()` dependency for the test compile configuration and a dependency on the plugin classpath manifest generation task for the test runtime configuration. Default conventions can
 * be customized with the help of [GradlePluginDevelopmentExtension].
 *
 * Integrates with the 'maven-publish' and 'ivy-publish' plugins to automatically publish the plugins so they can be resolved using the `pluginRepositories` and `plugins` DSL.
 *
 * @see [Gradle plugin development reference](https://docs.gradle.org/current/userguide/java_gradle_plugin.html)
 */
@NullMarked
abstract class JavaGradlePluginPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaLibraryPlugin::class.java)
        val extension = createExtension(project)
        applyDependencies(project)
        configureJarTask(project, extension)
        configureTestKit(project, extension)
        configurePublishing(project)
        registerPlugins(project, extension)
        configureDescriptorGeneration(project, extension)
        validatePluginDeclarations(project, extension)
        configurePluginValidations(project, extension)
        configureDependencyGradlePluginsResolution(project)
    }

    private fun registerPlugins(project: Project, extension: GradlePluginDevelopmentExtension) {
        val projectInternal = project as ProjectInternal
        val registry = projectInternal.getServices().get<ProjectPublicationRegistry?>(ProjectPublicationRegistry::class.java)
        val projectIdentity = projectInternal.getProjectIdentity()
        extension.getPlugins().all(Action { pluginDeclaration: PluginDeclaration? ->
            pluginDeclaration!!.setId(pluginDeclaration.getName())
            registry!!.registerPublication(projectIdentity, JavaGradlePluginPlugin.LocalPluginPublication(pluginDeclaration))
        })
    }

    private fun configureJarTask(project: Project, extension: GradlePluginDevelopmentExtension) {
        project.getTasks().named<Jar>(JAR_TASK, Jar::class.java, Action { jarTask: Jar ->
            val actionsState = PluginValidationActionsState()
            val pluginDescriptorCollector = PluginDescriptorCollectorAction(actionsState)
            val classManifestCollector = ClassManifestCollectorAction(actionsState)
            val pluginsProvider = project.provider<MutableCollection<PluginDeclaration>>(Callable { extension.getPlugins().getAsMap().values })
            val pluginValidationAction = PluginValidationAction(pluginsProvider, actionsState)

            jarTask.filesMatching(PLUGIN_DESCRIPTOR_PATTERN, pluginDescriptorCollector)
            jarTask.filesMatching(CLASSES_PATTERN, classManifestCollector)
            jarTask.appendParallelSafeAction(pluginValidationAction)
        })
    }

    private fun createExtension(project: Project): GradlePluginDevelopmentExtension {
        val defaultPluginSourceSet = JavaPluginHelper.getJavaComponent(project).mainFeature.sourceSet
        val defaultTestSourceSet = JavaPluginHelper.getDefaultTestSuite(project).sources
        return project.getExtensions().create<GradlePluginDevelopmentExtension>(EXTENSION_NAME, GradlePluginDevelopmentExtension::class.java, project, defaultPluginSourceSet!!, defaultTestSourceSet)
    }

    private fun configureTestKit(project: Project, extension: GradlePluginDevelopmentExtension) {
        val pluginUnderTestMetadataTask = createAndConfigurePluginUnderTestMetadataTask(project, extension)
        establishTestKitAndPluginClasspathDependencies(project, extension, pluginUnderTestMetadataTask)
    }

    private fun createAndConfigurePluginUnderTestMetadataTask(project: Project, extension: GradlePluginDevelopmentExtension): TaskProvider<PluginUnderTestMetadata> {
        return project.getTasks()
            .register<PluginUnderTestMetadata>(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata::class.java, Action { pluginUnderTestMetadataTask: PluginUnderTestMetadata ->
                pluginUnderTestMetadataTask.setGroup(PLUGIN_DEVELOPMENT_GROUP)
                pluginUnderTestMetadataTask.setDescription(PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION)

                pluginUnderTestMetadataTask.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(pluginUnderTestMetadataTask.getName()))
                pluginUnderTestMetadataTask.getPluginClasspath().from(classpathForPlugin(project, extension))
            })
    }

    private fun establishTestKitAndPluginClasspathDependencies(project: Project, extension: GradlePluginDevelopmentExtension, pluginClasspathTask: TaskProvider<PluginUnderTestMetadata>) {
        project.afterEvaluate(JavaGradlePluginPlugin.TestKitAndPluginClasspathDependenciesAction(extension, pluginClasspathTask))
    }

    private fun configurePublishing(project: Project) {
        project.getPluginManager().withPlugin("maven-publish", Action { appliedPlugin: AppliedPlugin? -> project.getPluginManager().apply(MavenPluginPublishPlugin::class.java) })
        project.getPluginManager().withPlugin("ivy-publish", Action { appliedPlugin: AppliedPlugin? -> project.getPluginManager().apply(IvyPluginPublishingPlugin::class.java) })
    }

    private fun configureDescriptorGeneration(project: Project, extension: GradlePluginDevelopmentExtension) {
        val generatePluginDescriptors =
            project.getTasks().register<GeneratePluginDescriptors>(GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME, GeneratePluginDescriptors::class.java, Action { task: GeneratePluginDescriptors ->
                task.setGroup(PLUGIN_DEVELOPMENT_GROUP)
                task.setDescription(GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION)
                task.getDeclarations().set(extension.getPlugins())
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()))
            })
        project.getTasks().named<Copy>(PROCESS_RESOURCES_TASK, Copy::class.java, Action { task: Copy ->
            val copyPluginDescriptors: CopySpec = task.getRootSpec().addChild()
            copyPluginDescriptors.into("META-INF/gradle-plugins")
            copyPluginDescriptors.from(generatePluginDescriptors)
        })
    }

    private fun validatePluginDeclarations(project: Project, extension: GradlePluginDevelopmentExtension) {
        project.afterEvaluate(Action { evaluatedProject: Project? ->
            for (declaration in extension.getPlugins()) {
                requireNotNull(declaration.getId()) { String.format(DECLARATION_MISSING_ID_MESSAGE, declaration.getName()) }
                requireNotNull(declaration.getImplementationClass()) { String.format(DECLARATION_MISSING_IMPLEMENTATION_MESSAGE, declaration.getName()) }
            }
        })
    }

    private fun configurePluginValidations(project: Project, extension: GradlePluginDevelopmentExtension) {
        val validatorTask = project.getTasks().register<ValidatePlugins>(VALIDATE_PLUGINS_TASK_NAME, ValidatePlugins::class.java, Action { task: ValidatePlugins ->
            task.setGroup(PLUGIN_DEVELOPMENT_GROUP)
            task.setDescription(VALIDATE_PLUGIN_TASK_DESCRIPTION)

            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("reports/plugin-development/validation-report.json"))

            task.getClasses().setFrom(Callable { extension.getPluginSourceSet().getOutput().getClassesDirs() } as Callable<Any?>)
            task.getClasspath().setFrom( // Use the plugin's runtimeClasspath (own classes + runtime dependencies) for validation and also add the compileClasspath in case some compileOnly
                // dependencies need to be loaded. Prefer the runtime classpath in case of clashes by adding it first.
                Callable { extension.getPluginSourceSet().getRuntimeClasspath() } as Callable<Any?>,
                Callable { extension.getPluginSourceSet().getCompileClasspath() } as Callable<Any?>
            )
        })
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, Action { check: Task? -> check!!.dependsOn(validatorTask) })

        // Published plugins get stricter validation by default
        project.getPluginManager().withPlugin("publishing", Action { p: AppliedPlugin? -> enableStricterValidation(validatorTask) })
        project.getPluginManager().withPlugin("com.gradle.plugin-publish", Action { p: AppliedPlugin? -> enableStricterValidation(validatorTask) })
    }

    private fun configureDependencyGradlePluginsResolution(project: Project) {
        GradlePluginApiVersionAttributeConfigurationAction().execute(project as ProjectInternal)
    }

    /**
     * A state shared by the validation process.
     *
     *
     * This separate class is required to ensure the shared state remains shared after deserialization of actions from the configuration cache.
     *
     * @see .configureJarTask
     */
    internal class PluginValidationActionsState @JvmOverloads constructor(
        val collectedDescriptors: MutableList<PluginDescriptor> = ArrayList<PluginDescriptor>(),
        val collectedClasses: MutableSet<String> = HashSet<String>()
    ) {
        fun addPluginClass(className: String) {
            collectedClasses.add(className)
        }

        fun addPluginDescriptor(descriptor: PluginDescriptor) {
            collectedDescriptors.add(descriptor)
        }
    }

    /**
     * Implements plugin validation tasks to validate that a proper plugin jar is produced.
     */
    internal class PluginValidationAction(private val plugins: Provider<MutableCollection<PluginDeclaration>>, private val actionsState: PluginValidationActionsState) : Action<Task> {
        override fun execute(task: Task) {
            val descriptors = actionsState.collectedDescriptors
            if (descriptors.isEmpty()) {
                LOGGER.warn(String.format(NO_DESCRIPTOR_WARNING_MESSAGE, task.getPath()))
            } else {
                val pluginFileNames: MutableSet<String> = HashSet<String>()
                for (descriptor in descriptors) {
                    var descriptorURI: URI? = null
                    try {
                        descriptorURI = descriptor.getPropertiesFileUrl().toURI()
                    } catch (e: URISyntaxException) {
                        // Do nothing since the only side effect is that we wouldn't
                        // be able to log the plugin descriptor file name.  Shouldn't
                        // be a reasonable scenario where this occurs since these
                        // descriptors should be generated from real files.
                    }
                    val pluginFileName = if (descriptorURI != null) File(descriptorURI).getName() else "UNKNOWN"
                    pluginFileNames.add(pluginFileName)
                    val pluginImplementation = descriptor.getImplementationClassName()
                    if (pluginImplementation.length == 0) {
                        LOGGER.warn(String.format(INVALID_DESCRIPTOR_WARNING_MESSAGE, task.getPath(), pluginFileName))
                    } else if (!hasFullyQualifiedClass(pluginImplementation)) {
                        LOGGER.warn(String.format(BAD_IMPL_CLASS_WARNING_MESSAGE, task.getPath(), pluginFileName, pluginImplementation))
                    }
                }
                for (declaration in plugins.get()) {
                    if (!pluginFileNames.contains(declaration.getId() + ".properties")) {
                        LOGGER.warn(String.format(DECLARED_PLUGIN_MISSING_MESSAGE, task.getPath(), declaration.getName(), declaration.getId()))
                    }
                }
            }
        }

        fun hasFullyQualifiedClass(fqClass: String): Boolean {
            return actionsState.collectedClasses.contains(fqClass.replace("\\.".toRegex(), "/") + ".class")
        }
    }

    /**
     * A file copy action that collects plugin descriptors as they are added to the jar.
     */
    internal class PluginDescriptorCollectorAction(private val actionsState: PluginValidationActionsState) : Action<FileCopyDetails> {
        override fun execute(fileCopyDetails: FileCopyDetails) {
            val descriptor: PluginDescriptor?
            try {
                descriptor = PluginDescriptor(fileCopyDetails.getFile().toURI().toURL())
            } catch (e: MalformedURLException) {
                // Not sure under what scenario (if any) this would occur,
                // but there's no sense in collecting the descriptor if it does.
                return
            }
            if (descriptor.getImplementationClassName() != null) {
                actionsState.addPluginDescriptor(descriptor)
            }
        }
    }

    /**
     * A file copy action that collects class file paths as they are added to the jar.
     */
    internal class ClassManifestCollectorAction(private val actionsState: PluginValidationActionsState) : Action<FileCopyDetails> {
        override fun execute(fileCopyDetails: FileCopyDetails) {
            actionsState.addPluginClass(fileCopyDetails.getRelativePath().toString())
        }
    }

    /**
     * An action that automatically declares the TestKit dependency for the test compile configuration and a dependency
     * on the plugin classpath manifest generation task for the test runtime configuration.
     */
    internal class TestKitAndPluginClasspathDependenciesAction private constructor(
        private val extension: GradlePluginDevelopmentExtension,
        private val pluginClasspathTask: TaskProvider<PluginUnderTestMetadata>
    ) : Action<Project> {
        override fun execute(project: Project) {
            val dependencies = project.getDependencies()
            val testSourceSets = extension.getTestSourceSets()
            project.getNormalization().getRuntimeClasspath().ignore(PluginUnderTestMetadata.Companion.METADATA_FILE_NAME)

            val gradleInstallation = (project as ProjectInternal).getServices()
                .get<org.gradle.internal.installation.CurrentGradleInstallation?>(org.gradle.internal.installation.CurrentGradleInstallation::class.java)!!.getInstallation()
            project.getTasks().withType<Test>(Test::class.java).configureEach(Action { test: Test? ->
                test!!.getInputs()
                    .files(pluginClasspathTask.map<ConfigurableFileCollection>(Transformer { obj: PluginUnderTestMetadata? -> obj!!.getPluginClasspath() }))
                    .withPropertyName("pluginClasspath")
                    .withNormalizer(ClasspathNormalizer::class.java)
                test.getJvmArgumentProviders().add(JavaGradlePluginPlugin.GradleJvmCommandLineArgumentProvider(test))
                if (gradleInstallation != null) {
                    test.getInputs()
                        .dir(gradleInstallation.getGradleHome())
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                        .withPropertyName("gradleHome")
                }
            })

            for (testSourceSet in testSourceSets) {
                val implementationConfigurationName = testSourceSet.implementationConfigurationName
                dependencies.add(implementationConfigurationName, dependencies.gradleTestKit())
                dependencies.add(implementationConfigurationName, dependencies.gradleApi())
                val runtimeOnlyConfigurationName = testSourceSet.runtimeOnlyConfigurationName
                dependencies.add(runtimeOnlyConfigurationName, project.getLayout().files(pluginClasspathTask))
            }
        }
    }

    /**
     * Provides JVM argumensts necessary to to run a Gradle daemon depending on the Gradle version used.
     * Needed when using ProjectBuilder in tests.
     */
    private class GradleJvmCommandLineArgumentProvider(private val test: Test) : CommandLineArgumentProvider {
        override fun asArguments(): Iterable<String> {
            val majorVersion = test.getJavaVersion().majorVersion.toInt()
            return JpmsConfiguration.forDaemonProcesses(majorVersion, true)
        }
    }

    private class LocalPluginPublication(private val pluginDeclaration: PluginDeclaration) : PluginPublication {
        override fun getDisplayName(): DisplayName {
            return Describables.withTypeAndName("plugin", pluginDeclaration.getName())
        }

        override fun getPluginId(): PluginId {
            return DefaultPluginId.of(pluginDeclaration.getId())
        }
    }

    companion object {
        private val LOGGER: Logger = getLogger(JavaGradlePluginPlugin::class.java)!!

        @Deprecated("")
        val API_CONFIGURATION: String = JvmConstants.API_CONFIGURATION_NAME
        const val JAR_TASK: String = "jar"
        const val PROCESS_RESOURCES_TASK: String = "processResources"
        const val GRADLE_PLUGINS: String = "gradle-plugins"
        val PLUGIN_DESCRIPTOR_PATTERN: String = "META-INF/" + GRADLE_PLUGINS + "/*.properties"
        const val CLASSES_PATTERN: String = "**/*.class"
        const val BAD_IMPL_CLASS_WARNING_MESSAGE: String = "%s: A valid plugin descriptor was found for %s but the implementation class %s was not found in the jar."
        const val INVALID_DESCRIPTOR_WARNING_MESSAGE: String = "%s: A plugin descriptor was found for %s but it was invalid."
        val NO_DESCRIPTOR_WARNING_MESSAGE: String = "%s: No valid plugin descriptors were found in META-INF/" + GRADLE_PLUGINS
        val DECLARED_PLUGIN_MISSING_MESSAGE: String = "%s: Could not find plugin descriptor of %s at META-INF/" + GRADLE_PLUGINS + "/%s.properties"
        const val DECLARATION_MISSING_ID_MESSAGE: String = "Missing id for %s"
        const val DECLARATION_MISSING_IMPLEMENTATION_MESSAGE: String = "Missing implementationClass for %s"
        const val EXTENSION_NAME: String = "gradlePlugin"
        const val PLUGIN_UNDER_TEST_METADATA_TASK_NAME: String = "pluginUnderTestMetadata"
        const val GENERATE_PLUGIN_DESCRIPTORS_TASK_NAME: String = "pluginDescriptors"
        const val VALIDATE_PLUGINS_TASK_NAME: String = "validatePlugins"

        /**
         * Suppress adding the `DependencyHandler#gradleApi()` dependency.
         *
         * Experimental property used to test using an external Gradle API dependency.
         */
        val EXPERIMENTAL_SUPPRESS_GRADLE_API_PROPERTY: InternalOption<Boolean> = InternalOptions.ofBoolean("org.gradle.unsafe.suppress-gradle-api", false)

        /**
         * The task group used for tasks created by the Java Gradle plugin development plugin.
         *
         * @since 4.0
         */
        const val PLUGIN_DEVELOPMENT_GROUP: String = "Plugin development"

        /**
         * The description for the task generating metadata for plugin functional tests.
         *
         * @since 4.0
         */
        const val PLUGIN_UNDER_TEST_METADATA_TASK_DESCRIPTION: String = "Generates the metadata for plugin functional tests."

        /**
         * The description for the task generating plugin descriptors from plugin declarations.
         *
         * @since 4.0
         */
        const val GENERATE_PLUGIN_DESCRIPTORS_TASK_DESCRIPTION: String = "Generates plugin descriptors from plugin declarations."

        /**
         * The description for the task validating the plugin.
         *
         * @since 6.0
         */
        const val VALIDATE_PLUGIN_TASK_DESCRIPTION: String = "Validates the plugin by checking parameter annotations on task and artifact transform types etc."

        private fun applyDependencies(project: Project) {
            // TODO This should be provided via GradlePluginDevelopmentExtension.gradleApiVersion once it's not an experimental feature
            val internalOptions = (project as ProjectInternal).getServices().get<InternalOptions?>(InternalOptions::class.java)
            if (internalOptions!!.getBoolean(EXPERIMENTAL_SUPPRESS_GRADLE_API_PROPERTY)) {
                return
            }
            val dependencies = project.getDependencies()
            dependencies.add(JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME, dependencies.gradleApi())
        }

        private fun classpathForPlugin(project: Project, extension: GradlePluginDevelopmentExtension): Callable<FileCollection> {
            return Callable {
                val sourceSet = extension.getPluginSourceSet()
                val runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName())
                val view = runtimeClasspath.getIncoming().artifactView(Action { config: ArtifactView.ViewConfiguration? ->
                    config!!.componentFilter(
                        SerializableLambdas.spec<ComponentIdentifier>(
                            SerializableLambdas.SerializableSpec { componentId: ComponentIdentifier -> excludeGradleApi(componentId) })
                    )
                }
                )
                project.getObjects().fileCollection().from(
                    sourceSet.getOutput(),
                    view.getFiles().getElements()
                )
            }
        }

        private fun excludeGradleApi(componentId: ComponentIdentifier): Boolean {
            if (componentId is OpaqueComponentIdentifier) {
                val classPathNotation = componentId.getClassPathNotation()
                return classPathNotation != DependencyFactoryInternal.ClassPathNotation.GRADLE_API && classPathNotation != DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
            }
            return true
        }

        private fun enableStricterValidation(validatePlugins: TaskProvider<ValidatePlugins>) {
            validatePlugins.configure(Action { task: ValidatePlugins? ->
                task!!.getEnableStricterValidation().convention(true)
            })
        }
    }
}
