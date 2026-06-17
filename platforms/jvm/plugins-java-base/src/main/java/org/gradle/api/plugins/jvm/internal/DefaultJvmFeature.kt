/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.DocsType
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSet.Companion.isMain
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.util.internal.TextUtil
import java.util.concurrent.Callable
import java.util.function.Consumer

/**
 * Represents a generic "Java feature", using the specified source set and its corresponding
 * configurations, compile task, and jar task. This feature creates a jar task and javadoc task, and
 * can optionally also create consumable javadoc and sources jar variants.
 *
 *
 * This can be used to create production libraries, applications, test suites, test fixtures,
 * or any other consumable JVM feature.
 *
 *
 * This feature can conditionally be configured to instead "extend" the production code. In that case, this
 * feature creates additional dependency configurations which live adjacent to the main source set's dependency scopes,
 * which allow users to declare optional dependencies that the production code will compile and test against.
 * These extra dependencies are not published as part of the production variants, but as separate apiElements
 * and runtimeElements variants as defined by this feature. Then, users can declare a dependency on this
 * feature to get access to the optional dependencies.
 *
 *
 * This "extending" functionality is fragile, in that it allows the production code to be compiled and
 * tested against dependencies which will not necessarily be present at runtime. For this reason, we are
 * planning to deprecate the "extending" functionality. For more information, see [.doExtendProductionCode].
 *
 *
 * For backwards compatibility reasons, when this feature is operating in the "extending" mode,
 * this feature is able to operate without the presence of the main feature, as long as the user
 * explicitly configures the project by manually creating a main and test source set themselves.
 * In that case, this feature will additionally create the jar and javadoc tasks which the main
 * source set would normally create. Additionally, this extension feature is able to create the
 * sources and javadoc variants that the main feature would also conditionally create.
 */
class DefaultJvmFeature(
    private val name: String,  // Should features just create the sourcesets they are going to use?  How can we ensure the same sourceset isn't used
    // by multiple features (and that the same feature isn't used by multiple components)?
    val sourceSet: SourceSet,
    private val capabilities: MutableSet<Capability>,
// Services
    private val project: ProjectInternal,
    private val extendProductionCode: Boolean
) : JvmFeatureInternal {
    private val jvmPluginServices: JvmPluginServices
    private val jvmLanguageUtilities: JvmLanguageUtilities

    // Tasks
    val jarTask: TaskProvider<Jar>
    val compileJavaTask: TaskProvider<JavaCompile>

    // Dependency configurations
    val implementationConfiguration: Configuration
    val runtimeOnlyConfiguration: Configuration
    val compileOnlyConfiguration: Configuration

    // Configurable dependency configurations
    private var compileOnlyApi: Configuration? = null
    private var api: Configuration? = null

    // Resolvable configurations
    val runtimeClasspathConfiguration: Configuration
    val compileClasspathConfiguration: Configuration

    // Outgoing variants
    val apiElementsConfiguration: NamedDomainObjectProvider<ConsumableConfiguration>
    val runtimeElementsConfiguration: NamedDomainObjectProvider<ConsumableConfiguration>

    // Configurable outgoing variants
    private var javadocElements: NamedDomainObjectProvider<ConsumableConfiguration>? = null
    private var sourcesElements: NamedDomainObjectProvider<ConsumableConfiguration>? = null

    init {
        // TODO: Deprecate allowing user to extend main feature.
        if (extendProductionCode && !isMain(sourceSet)) {
            throw GradleException("Cannot extend main feature if source set is not also main.")
        }

        this.jvmPluginServices = project.getServices().get<JvmPluginServices?>(JvmPluginServices::class.java)!!
        this.jvmLanguageUtilities = project.getServices().get<JvmLanguageUtilities?>(JvmLanguageUtilities::class.java)!!

        val configurations = project.getConfigurations()
        val tasks: TaskContainer = project.getTasks()

        this.compileJavaTask = tasks.< JavaCompile > named < org . gradle . api . tasks . compile . JavaCompile >(sourceSet.compileJavaTaskName, JavaCompile::class.java)
        this.jarTask = registerOrGetJarTask(sourceSet, tasks)

        // If extendProductionCode=false, the source set has already created these configurations.
        // If extendProductionCode=true, then we create new dependency scopes and later update the main and
        // test source sets to extend from them.
        this.implementationConfiguration = dependencyScope("Implementation", JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME, extendProductionCode, false)
        this.compileOnlyConfiguration = dependencyScope("Compile-only", JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME, extendProductionCode, false)
        this.runtimeOnlyConfiguration = dependencyScope("Runtime-only", JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME, extendProductionCode, false)

        this.runtimeClasspathConfiguration = configurations.getByName(sourceSet.runtimeClasspathConfigurationName!!)
        this.compileClasspathConfiguration = configurations.getByName(sourceSet.compileClasspathConfigurationName!!)

        val jarArtifact: PublishArtifact = LazyPublishArtifact(this.jarTask, project.getFileResolver(), project.getTaskDependencyFactory())
        this.apiElementsConfiguration = createApiElements(configurations, jarArtifact, this.compileJavaTask)
        this.runtimeElementsConfiguration = createRuntimeElements(configurations, jarArtifact, this.compileJavaTask)

        if (extendProductionCode) {
            doExtendProductionCode()
        }

        val javaPluginExtension = project.getExtensions().findByType<JavaPluginExtension>(JavaPluginExtension::class.java)
        JvmPluginsHelper.configureJavaDocTask("'" + name + "' feature", sourceSet, tasks, javaPluginExtension)
    }

    fun doExtendProductionCode() {
        // This method is one of the primary reasons that we want to deprecate the "extending" behavior. It updates
        // the main source set and test source set to "extend" this feature. That means any dependencies declared on
        // this feature's dependency configurations will be available locally, during compilation and runtime, to the main
        // production code and default test suite. However, when publishing the production code, these dependencies will
        // not be included in its consumable variants. Therefore, the main code is compiled _and tested_ against
        // dependencies which will not necessarily be available at runtime when it is consumed from other projects
        // or in its published form.
        //
        // This leads to a case where, in order for the production code to not throw NoClassDefFoundErrors during runtime,
        // it must detect the presence of the dependencies added by this feature, and then conditionally enable and disable
        // certain optional behavior. We do not want to promote this pattern.
        //
        // A much safer pattern would be to create normal features as opposed to an "extending" feature. Then, the normal
        // feature would have a project dependency on the main feature. It would provide an extra jar with any additional code,
        // and also bring along any extra dependencies that code requires. The main feature would then be able to detect the
        // presence of the feature through some {@code ServiceLoader} mechanism, as opposed to detecting the existence of
        // dependencies directly.
        //
        // This pattern is also more flexible than the "extending" pattern in that it allows features to extend arbitrary
        // features as opposed to just the main feature.

        val configurations: ConfigurationContainer = project.getConfigurations()
        val mainSourceSet: SourceSet = project.getExtensions().findByType<JavaPluginExtension>(JavaPluginExtension::class.java)!!
            .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME)!!

        // Update the main feature's source set to extend our "extension" feature's dependency scopes.
        configurations.getByName(mainSourceSet.compileClasspathConfigurationName!!).extendsFrom(this.implementationConfiguration, this.compileOnlyConfiguration)
        configurations.getByName(mainSourceSet.runtimeClasspathConfigurationName!!).extendsFrom(this.implementationConfiguration, this.runtimeOnlyConfiguration)
        // Update the default test suite's source set to extend our "extension" feature's dependency scopes.
        configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(this.implementationConfiguration)
        configurations.getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(this.implementationConfiguration, this.runtimeOnlyConfiguration)
    }

    /**
     * Hack to allow us to create configurations for normal and "extending" features. This should go away.
     */
    private fun getConfigurationName(suffix: String): String {
        if (extendProductionCode) {
            return name + StringUtils.capitalize(suffix)
        } else {
            return (sourceSet as DefaultSourceSet).configurationNameOf(suffix)
        }
    }

    private fun createApiElements(
        configurations: RoleBasedConfigurationContainerInternal,
        jarArtifact: PublishArtifact,
        compileJava: TaskProvider<JavaCompile>
    ): NamedDomainObjectProvider<ConsumableConfiguration> {
        val configName = getConfigurationName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
        return configurations.consumable(configName, Action { apiElements: ConsumableConfiguration? ->
            jvmLanguageUtilities.useDefaultTargetPlatformInference<JavaCompile?>(apiElements, compileJava)
            jvmPluginServices.configureAsApiElements(apiElements!!)
            capabilities.forEach(Consumer { notation: Capability? -> apiElements.getOutgoing().capability(notation!!) })
            apiElements.setDescription("API elements for the '" + name + "' feature.")

            // Configure artifact sets
            Companion.addJarArtifactToConfiguration(apiElements, jarArtifact)
        })
    }

    private fun createRuntimeElements(
        configurations: RoleBasedConfigurationContainerInternal,
        jarArtifact: PublishArtifact,
        compileJava: TaskProvider<JavaCompile>
    ): NamedDomainObjectProvider<ConsumableConfiguration> {
        val configName = getConfigurationName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
        return configurations.consumable(configName, Action { runtimeElements: ConsumableConfiguration? ->
            jvmLanguageUtilities.useDefaultTargetPlatformInference<JavaCompile?>(runtimeElements, compileJava)
            jvmPluginServices.configureAsRuntimeElements(runtimeElements!!)
            capabilities.forEach(Consumer { notation: Capability? -> runtimeElements.getOutgoing().capability(notation!!) })
            runtimeElements.setDescription("Runtime elements for the '" + name + "' feature.")

            runtimeElements.extendsFrom(this.implementationConfiguration, this.runtimeOnlyConfiguration)

            // Configure artifact sets
            Companion.addJarArtifactToConfiguration(runtimeElements, jarArtifact)
            jvmPluginServices.configureClassesDirectoryVariant(runtimeElements, sourceSet)
            jvmPluginServices.configureResourcesDirectoryVariant(runtimeElements, sourceSet)
        })
    }

    override fun withApi() {
        // If the Kotlin JVM plugin is applied, after it applies the Java plugin, it will create the API configuration.
        // We need to suppress the deprecation warning for creating duplicate configurations or else if the java-library
        // plugin was subsequently applied we'd get a warning that the API configuration was created twice.
        // * If Kotlin is always creating libraries then it should always apply the java-library plugin.
        // * Otherwise, if it could create an application, it should not automatically create the api configuration.
        this.api = dependencyScope("API", JvmConstants.API_CONFIGURATION_NAME, true, false)
        this.compileOnlyApi = dependencyScope("Compile-only API", JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME, true, true)

        this.apiElementsConfiguration.configure(Action { conf: ConsumableConfiguration? ->
            conf!!.extendsFrom(api!!, compileOnlyApi!!)
            // TODO: Why do we not always do this? Why only when we have an API?
            jvmPluginServices.configureClassesDirectoryVariant(conf, sourceSet)
        })
        this.implementationConfiguration.extendsFrom(api!!)
        this.compileOnlyConfiguration.extendsFrom(compileOnlyApi!!)

        if (extendProductionCode) {
            project.getConfigurations().getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(compileOnlyApi!!)
        }
    }

    override fun withSourceElements() {
        // TODO: Why are we using this non-standard name? For the `java` component, this
        // equates to `mainSourceElements` instead of `sourceElements` as one would expect.
        // Can we change this name without breaking compatibility? Is the variant name part
        // of the component's API?
        val variantName = this.sourceSet.name + SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX

        project.getConfigurations().consumable(variantName, Action { variant: ConsumableConfiguration? ->
            variant!!.setDescription("List of source directories contained in the Main SourceSet.")
            variant.extendsFrom(this.implementationConfiguration)

            jvmPluginServices.configureAsSources(variant)
            variant.getOutgoing().artifacts(
                this.sourceSet.allSource!!.getSourceDirectories().getElements().flatMap({ e -> project.provider<T>(Callable { e }) }),
                Action { artifact: ConfigurablePublishArtifact? -> artifact!!.setType(ArtifactTypeDefinition.DIRECTORY_TYPE) }
            )
        })
    }

    private fun dependencyScope(kind: String, suffix: String, create: Boolean, warnOnDuplicate: Boolean): Configuration {
        val configName = getConfigurationName(suffix)
        val configuration = if (create)
            project.getConfigurations().maybeCreateDependencyScopeLocked(configName, warnOnDuplicate)
        else
            project.getConfigurations().getByName(configName)
        configuration.setDescription(kind + " dependencies for the '" + name + "' feature.")
        return configuration
    }

    private fun registerOrGetJarTask(sourceSet: SourceSet, tasks: TaskContainer): TaskProvider<Jar> {
        val jarTaskName: String = sourceSet.jarTaskName!!
        if (!tasks.getNames().contains(jarTaskName)) {
            return tasks.register<Jar>(jarTaskName, Jar::class.java, Action { jar: Jar ->
                jar.setDescription("Assembles a jar archive containing the classes of the '" + name + "' feature.")
                jar.setGroup(BasePlugin.BUILD_GROUP)
                jar.from(sourceSet.output)
                if (!capabilities.isEmpty()) {
                    jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name))
                }
            })
        }
        return tasks.named<Jar>(jarTaskName, Jar::class.java)
    }

    override fun getName(): String {
        return name
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return ImmutableCapabilities.of(capabilities)
    }

    val apiConfiguration: Configuration
        get() = api

    val compileOnlyApiConfiguration: Configuration
        get() = compileOnlyApi

    override fun maybeRegisterJavadocElements(): NamedDomainObjectProvider<ConsumableConfiguration> {
        if (javadocElements == null) {
            this.javadocElements = JvmPluginsHelper.createInternalDocumentationVariantWithArtifact(
                sourceSet.javadocElementsConfigurationName,
                if (isMain(sourceSet)) null else name,
                DocsType.JAVADOC,
                capabilities,
                sourceSet.javadocJarTaskName,
                project.getTasks().named(sourceSet.javadocTaskName!!),
                project
            )
        }
        return javadocElements!!
    }

    override fun maybeRegisterSourcesElements(): NamedDomainObjectProvider<ConsumableConfiguration> {
        if (sourcesElements == null) {
            this.sourcesElements = JvmPluginsHelper.createInternalDocumentationVariantWithArtifact(
                sourceSet.sourcesElementsConfigurationName,
                if (isMain(sourceSet)) null else name,
                DocsType.SOURCES,
                capabilities,
                sourceSet.sourcesJarTaskName,
                sourceSet.allSource,
                project
            )
        }
        return sourcesElements!!
    }

    companion object {
        private const val SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements"

        private fun addJarArtifactToConfiguration(configuration: Configuration, jarArtifact: PublishArtifact) {
            val publications = configuration.getOutgoing()

            // Configure an implicit variant
            publications.getArtifacts().add(jarArtifact)
            publications.getAttributes().attribute<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        }
    }
}
