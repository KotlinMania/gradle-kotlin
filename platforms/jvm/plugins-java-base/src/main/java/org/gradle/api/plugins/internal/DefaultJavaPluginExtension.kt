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
package org.gradle.api.plugins.internal

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.jvm.ModularitySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.plugins.JavaResolutionConsistency
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.Actions
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.jvm.DefaultModularitySpec
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.jvm.toolchain.internal.JavaToolchainSpecInternal
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.ConfigureUtil
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Default implementation of [JavaPluginExtension].
 *
 * This extension is used to implicitly configure all JVM-related [org.gradle.api.component.Component]s
 * in the project.  Some methods - such as [.registerFeature] -
 * are not applicable in this manner and will throw exceptions if used when multiple
 * [JvmSoftwareComponentInternal][JvmSoftwareComponentInternal]
 * components are present.
 *
 * At present there should only ever be one such component - the `java` component added by the [JavaPlugin][org.gradle.api.plugins.JavaPlugin] - but
 * multiple components may be created by JVM language plugins in the future.
 */
abstract class DefaultJavaPluginExtension @Inject constructor(private val project: ProjectInternal, private val sourceSets: SourceSetContainer, toolchainSpec: DefaultToolchainSpec?) :
    JavaPluginExtensionInternal {
    private val toolchainSpec: JavaToolchainSpecInternal?
    private val objectFactory: ObjectFactory
    private val modularity: ModularitySpec
    private val toolchain: JavaToolchainSpec?

    var rawSourceCompatibility: JavaVersion? = null
        private set
    var rawTargetCompatibility: JavaVersion? = null
        private set

    init {
        this.toolchainSpec = toolchainSpec
        this.objectFactory = project.getObjects()
        this.modularity = objectFactory.newInstance<DefaultModularitySpec>(DefaultModularitySpec::class.java)
        this.toolchain = toolchainSpec
        configureDefaults()
    }

    private fun configureDefaults() {
        getAutoTargetJvm().convention(true)
        getDocsDir().convention(project.getLayout().getBuildDirectory().dir("docs"))
        getTestResultsDir().convention(project.getLayout().getBuildDirectory().dir(TestingBasePlugin.TEST_RESULTS_DIR_NAME))
        getTestReportDir().convention(project.getExtensions().getByType<ReportingExtension?>(ReportingExtension::class.java).getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME))
    }

    override fun sourceSets(closure: Closure<*>): Any {
        return sourceSets.configure(closure)
    }

    override fun sourceSets(action: Action<in SourceSetContainer?>) {
        action.execute(getSourceSets())
    }

    override fun getSourceCompatibility(): JavaVersion? {
        if (this.rawSourceCompatibility != null) {
            return this.rawSourceCompatibility
        } else if (toolchainSpec != null && toolchainSpec.isConfigured()) {
            return JavaVersion.toVersion(toolchainSpec.languageVersion.get().toString())
        } else {
            return JavaVersion.current()
        }
    }

    override fun setSourceCompatibility(value: Any?) {
        setSourceCompatibility(JavaVersion.toVersion(value))
    }

    override fun setSourceCompatibility(value: JavaVersion?) {
        this.rawSourceCompatibility = value
    }

    override fun getTargetCompatibility(): JavaVersion? {
        return if (this.rawTargetCompatibility != null) this.rawTargetCompatibility else getSourceCompatibility()
    }

    override fun setTargetCompatibility(value: Any?) {
        setTargetCompatibility(JavaVersion.toVersion(value))
    }

    override fun setTargetCompatibility(value: JavaVersion?) {
        this.rawTargetCompatibility = value
    }

    override fun manifest(): Manifest {
        return manifest(Actions.doNothing<Manifest?>())
    }

    override fun manifest(closure: Closure<*>?): Manifest? {
        return ConfigureUtil.configure<Manifest?>(closure, createManifest())
    }

    override fun manifest(action: Action<in Manifest?>): Manifest {
        val manifest = createManifest()
        action.execute(manifest)
        return manifest
    }

    private fun createManifest(): Manifest {
        return DefaultManifest(project.getFileResolver())
    }

    override fun getSourceSets(): SourceSetContainer {
        return sourceSets
    }

    override fun disableAutoTargetJvm() {
        this.getAutoTargetJvm().set(false)
    }

    override fun getAutoTargetJvmDisabled(): Boolean {
        return !getAutoTargetJvm().get()
    }

    /**
     * @implNote throws an exception if used when multiple [JvmSoftwareComponentInternal] components are present.
     */
    override fun registerFeature(name: String, configureAction: Action<in FeatureSpec?>) {
        val spec = DefaultJavaFeatureSpec(validateFeatureName(name), project)
        configureAction.execute(spec)
        val feature = spec.create()

        // TODO: In Gradle 10 when we can guarantee that there is a Java component, we can remove these side-effects.
        if (spec.hasJavadocJar()) {
            feature.maybeRegisterJavadocElements()
        }
        if (spec.hasSourcesJar()) {
            feature.maybeRegisterSourcesElements()
        }

        val component = this.singleJavaComponent
        if (component != null) {
            component.features.add(feature)

            // TODO: Much of the logic below should become automatic.
            // The component should be aware of all variants in its features and should advertise them
            // without needing to explicitly know about each variant.
            val adhocComponent = component as AdhocComponentWithVariants
            if (spec.hasJavadocJar()) {
                val javadocElements = feature.maybeRegisterJavadocElements()
                adhocComponent.addVariantsFromConfiguration(javadocElements, JavaConfigurationVariantMapping("runtime", true))
            }

            if (spec.hasSourcesJar()) {
                val sourcesElements = feature.maybeRegisterSourcesElements()
                adhocComponent.addVariantsFromConfiguration(sourcesElements, JavaConfigurationVariantMapping("runtime", true))
            }

            if (spec.isPublished()) {
                adhocComponent.addVariantsFromConfiguration(feature.apiElementsConfiguration, JavaConfigurationVariantMapping("compile", true, feature.compileClasspathConfiguration))
                adhocComponent.addVariantsFromConfiguration(feature.runtimeElementsConfiguration, JavaConfigurationVariantMapping("runtime", true, feature.runtimeClasspathConfiguration))
            }
        }
    }

    private val singleJavaComponent: JvmSoftwareComponentInternal?
        get() {
            val jvmComponents =
                project.getComponents().withType<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java)
            if (jvmComponents.size > 1) {
                val componentNames = CollectionUtils.join(", ", jvmComponents.getNames())
                throw InvalidUserCodeException("Cannot register feature because multiple JVM components are present. The following components were found: " + componentNames)
            } else if (!jvmComponents.isEmpty()) {
                return jvmComponents.iterator().next()
            }

            DeprecationLogger.deprecateBehaviour("The `registerFeature` method was called, but the Java plugin has not yet been applied.")
                .withContext("`registerFeature` should only be called in projects where the Java plugin has been applied.")!!
                .withAdvice("Apply the `java`, `java-library`, `application`, `groovy`, or any other plugin that applies the Java plugin.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "deprecate_register_feature_no_java_plugin")!!
                .nagUser()

            return null
        }

    override fun withJavadocJar() {
        project.getComponents().withType<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java).configureEach(Action { obj: JvmSoftwareComponentInternal? -> obj!!.withJavadocJar() })
    }

    override fun withSourcesJar() {
        project.getComponents().withType<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java).configureEach(Action { obj: JvmSoftwareComponentInternal? -> obj!!.withSourcesJar() })
    }

    override fun getModularity(): ModularitySpec {
        return modularity
    }

    override fun getToolchain(): JavaToolchainSpec? {
        return toolchain
    }

    override fun toolchain(action: Action<in JavaToolchainSpec?>): JavaToolchainSpec? {
        action.execute(toolchain)
        return toolchain
    }

    override fun consistentResolution(action: Action<in JavaResolutionConsistency?>) {
        val components = project.getComponents()
        val configurations: ConfigurationContainer = project.getConfigurations()
        val sourceSets = getSourceSets()
        action.execute(project.getObjects().newInstance<DefaultJavaResolutionConsistency?>(DefaultJavaResolutionConsistency::class.java, components, sourceSets, configurations))
    }

    class DefaultJavaResolutionConsistency @Inject constructor(
        private val components: SoftwareComponentContainer,
        private val sourceSets: SourceSetContainer,
        private val configurations: ConfigurationContainer
    ) : JavaResolutionConsistency {
        override fun useCompileClasspathVersions() {
            sourceSets.configureEach(Action { sourceSet: SourceSet? -> this.applyCompileClasspathConsistency(sourceSet!!) })
            components.withType<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java)
                .configureEach(Action { obj: JvmSoftwareComponentInternal? -> obj!!.useCompileClasspathConsistency() })
        }

        override fun useRuntimeClasspathVersions() {
            sourceSets.configureEach(Action { sourceSet: SourceSet? -> this.applyRuntimeClasspathConsistency(sourceSet!!) })
            components.withType<JvmSoftwareComponentInternal?>(JvmSoftwareComponentInternal::class.java)
                .configureEach(Action { obj: JvmSoftwareComponentInternal? -> obj!!.useRuntimeClasspathConsistency() })
        }

        private fun applyCompileClasspathConsistency(sourceSet: SourceSet) {
            val compileClasspath = findConfiguration(sourceSet.compileClasspathConfigurationName!!)
            val runtimeClasspath = findConfiguration(sourceSet.runtimeClasspathConfigurationName!!)
            runtimeClasspath.shouldResolveConsistentlyWith(compileClasspath)
        }

        private fun applyRuntimeClasspathConsistency(sourceSet: SourceSet) {
            val compileClasspath = findConfiguration(sourceSet.compileClasspathConfigurationName!!)
            val runtimeClasspath = findConfiguration(sourceSet.runtimeClasspathConfigurationName!!)
            compileClasspath.shouldResolveConsistentlyWith(runtimeClasspath)
        }

        private fun findConfiguration(configName: String): Configuration {
            return configurations.getByName(configName)
        }
    }

    companion object {
        private val VALID_FEATURE_NAME: Pattern = Pattern.compile("[a-zA-Z0-9]+")
        private fun validateFeatureName(name: String): String {
            if (!VALID_FEATURE_NAME.matcher(name).matches()) {
                throw InvalidUserDataException("Invalid feature name '" + name + "'. Must match " + VALID_FEATURE_NAME.pattern())
            }
            return name
        }
    }
}
