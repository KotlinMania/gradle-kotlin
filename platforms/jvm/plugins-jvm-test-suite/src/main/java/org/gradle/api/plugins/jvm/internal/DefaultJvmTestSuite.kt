/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.testing.Test
import org.gradle.api.testing.toolchains.internal.FrameworkCachingJvmTestToolchain
import org.gradle.api.testing.toolchains.internal.JUnit4TestToolchain
import org.gradle.api.testing.toolchains.internal.JUnit4ToolchainParameters
import org.gradle.api.testing.toolchains.internal.JUnitJupiterTestToolchain
import org.gradle.api.testing.toolchains.internal.JUnitJupiterToolchainParameters
import org.gradle.api.testing.toolchains.internal.JvmTestToolchain
import org.gradle.api.testing.toolchains.internal.JvmTestToolchainParameters
import org.gradle.api.testing.toolchains.internal.KotlinTestTestToolchain
import org.gradle.api.testing.toolchains.internal.KotlinTestToolchainParameters
import org.gradle.api.testing.toolchains.internal.LegacyJUnit4TestToolchain
import org.gradle.api.testing.toolchains.internal.SpockTestToolchain
import org.gradle.api.testing.toolchains.internal.SpockToolchainParameters
import org.gradle.api.testing.toolchains.internal.TestNGTestToolchain
import org.gradle.api.testing.toolchains.internal.TestNGToolchainParameters
import org.gradle.internal.Actions
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolated.IsolationScheme
import org.gradle.internal.service.ServiceLookup
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function
import javax.inject.Inject

/**
 * Default implementation of a [JvmTestSuite].
 *
 *
 * This class provides the basic functionality for creating and managing a JVM test suite, including
 * configuring the source set, wiring dependencies, and creating test targets.
 *
 *
 * The default test suite (named [JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME]) will default to using the
 * JUnit 4 test framework for backwards compatibility.  Any other test suite will default to using the JUnit Jupiter test framework.
 */
abstract class DefaultJvmTestSuite @Inject constructor(
    private val name: String,
    sourceSets: SourceSetContainer,
    configurations: ConfigurationContainer,
    private val taskDependencyFactory: TaskDependencyFactory
) : JvmTestSuite {
    private val sourceSet: SourceSet
    private val toolchainFactory: ToolchainFactory

    init {
        this.sourceSet = sourceSets.create(getName())!!
        this.toolchainFactory = ToolchainFactory(this.objectFactory, this.parentServices, this.instantiatorFactory)

        getTargets()!!.registerBinding<JvmTestSuiteTarget>(JvmTestSuiteTarget::class.java, DefaultJvmTestSuiteTarget::class.java)

        if (name == JvmTestSuitePlugin.Companion.DEFAULT_TEST_SUITE_NAME) {
            // for the built-in test suite, we don't express an opinion, so we will not add any dependencies
            // if a user explicitly calls useJUnit or useJUnitJupiter, the built-in test suite will behave like a custom one
            // and add dependencies automatically.
            this.testToolchain.convention(toolchainFactory.getOrCreate<LegacyJUnit4TestToolchain>(LegacyJUnit4TestToolchain::class.java))
        } else {
            val toolchain: JvmTestToolchain<out JUnitJupiterToolchainParameters> = toolchainFactory.getOrCreate<JUnitJupiterTestToolchain>(JUnitJupiterTestToolchain::class.java)!!
            this.testToolchain.convention(toolchain)
            toolchain.getParameters().getJupiterVersion().convention(JUnitJupiterTestToolchain.Companion.DEFAULT_VERSION)
        }

        // Add test framework dependencies to the dependency collectors BEFORE wiring them to configurations.
        // This ordering is critical because:
        // 1. configurations.named() executes immediately when the configuration already exists
        // 2. fromDependencyCollector() wires the collector to the configuration
        // 3. If cross-project configuration accesses allDependencies, it can trigger finalization
        //    of the collector's dependencies property (due to finalizeValueOnRead)
        // 4. bundle() must be called before this happens, otherwise adding to a finalized property fails
        addTestFrameworkDependenciesToDependencies()

        configurations.named(sourceSet.compileOnlyConfigurationName!!, Action { compileOnly: Configuration? -> compileOnly!!.fromDependencyCollector(getDependencies().compileOnly) })
        configurations.named(sourceSet.implementationConfigurationName!!, Action { implementation: Configuration? -> implementation!!.fromDependencyCollector(getDependencies().implementation) })
        configurations.named(sourceSet.runtimeOnlyConfigurationName!!, Action { runtimeOnly: Configuration? -> runtimeOnly!!.fromDependencyCollector(getDependencies().runtimeOnly) })
        configurations.named(
            sourceSet.annotationProcessorConfigurationName!!,
            Action { annotationProcessor: Configuration? -> annotationProcessor!!.fromDependencyCollector(getDependencies().annotationProcessor) })

        addDefaultTestTarget()

        getTargets()!!.withType<JvmTestSuiteTarget>(JvmTestSuiteTarget::class.java)
            .configureEach(Action { target: JvmTestSuiteTarget? -> target!!.getTestTask().configure(Action { task: Test? -> this.initializeTestFramework(task!!) }) })
    }

    private fun addTestFrameworkDependenciesToDependencies() {
        val dependencies = getDependencies()

        dependencies.compileOnly.bundle(this.testToolchain.map<S>(Transformer { obj: JvmTestToolchain<*>? -> obj!!.getCompileOnlyDependencies() }))
        dependencies.implementation.bundle(this.testToolchain.map<S>(Transformer { obj: JvmTestToolchain<*>? -> obj!!.getImplementationDependencies() }))
        dependencies.runtimeOnly.bundle(this.testToolchain.map<S>(Transformer { obj: JvmTestToolchain<*>? -> obj!!.getRuntimeOnlyDependencies() }))
    }

    private fun initializeTestFramework(task: Test) {
        // The Test task's testing framework is derived from the test suite's toolchain
        task.getTestFrameworkProperty().convention(this.testToolchain.map<TestFramework>(Transformer { toolchain: JvmTestToolchain<*>? -> toolchain!!.createTestFramework(task) }))
    }

    private fun addDefaultTestTarget() {
        val target: String
        if (getName() == JvmTestSuitePlugin.Companion.DEFAULT_TEST_SUITE_NAME) {
            target = JvmConstants.TEST_TASK_NAME
        } else {
            target = getName() // For now, we'll just name the test task for the single target for the suite with the suite name
        }

        getTargets()!!.register(target)
    }

    protected abstract val testToolchain: Property<JvmTestToolchain<*>>?

    override fun getName(): String {
        return name
    }

    @get:Inject
    abstract val objectFactory: ObjectFactory?

    @get:Inject
    abstract val providerFactory: ProviderFactory?

    @get:Inject
    abstract val instantiatorFactory: InstantiatorFactory?

    @get:Inject
    abstract val parentServices: ServiceRegistry?

    override fun getSources(): SourceSet {
        return sourceSet
    }

    override fun sources(configuration: Action<in SourceSet>) {
        configuration.execute(getSources())
    }

    abstract override fun getTargets(): ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget>?

    override fun useJUnit() {
        useJUnit(Actions.doNothing<JUnit4ToolchainParameters>())
    }

    override fun useJUnit(version: String) {
        useJUnit(this.providerFactory.provider<String>(Callable { version }))
    }

    override fun useJUnit(version: Provider<String>) {
        useJUnit(Action { parameters: JUnit4ToolchainParameters -> parameters.getVersion().set(version) })
    }

    private fun useJUnit(action: Action<JUnit4ToolchainParameters>) {
        setToolchainAndConfigure<JUnit4ToolchainParameters>(JUnit4TestToolchain::class.java, Action { parameters: JUnit4ToolchainParameters ->
            parameters.getVersion().convention(JUnit4TestToolchain.Companion.DEFAULT_VERSION)
            action.execute(parameters)
        })
    }

    override fun useJUnitJupiter() {
        useJUnitJupiter(Actions.doNothing<JUnitJupiterToolchainParameters>())
    }

    override fun useJUnitJupiter(version: String) {
        useJUnitJupiter(this.providerFactory.provider<String>(Callable { version }))
    }

    override fun useJUnitJupiter(version: Provider<String>) {
        useJUnitJupiter(Action { parameters: JUnitJupiterToolchainParameters -> parameters.getJupiterVersion().set(version) })
    }

    private fun useJUnitJupiter(action: Action<JUnitJupiterToolchainParameters>) {
        setToolchainAndConfigure<JUnitJupiterToolchainParameters>(JUnitJupiterTestToolchain::class.java, Action { parameters: JUnitJupiterToolchainParameters ->
            parameters.getJupiterVersion().convention(JUnitJupiterTestToolchain.Companion.DEFAULT_VERSION)
            action.execute(parameters)
        })
    }

    override fun useSpock() {
        useSpock(Actions.doNothing<SpockToolchainParameters>())
    }

    override fun useSpock(version: String) {
        useSpock(this.providerFactory.provider<String>(Callable { version }))
    }

    override fun useSpock(version: Provider<String>) {
        useSpock(Action { parameters: SpockToolchainParameters -> parameters.getSpockVersion().set(version) })
    }

    private fun useSpock(action: Action<SpockToolchainParameters>) {
        setToolchainAndConfigure<SpockToolchainParameters>(SpockTestToolchain::class.java, Action { parameters: SpockToolchainParameters ->
            parameters.getSpockVersion().convention(SpockTestToolchain.Companion.DEFAULT_VERSION)
            action.execute(parameters)
        })
    }

    override fun useKotlinTest() {
        useKotlinTest(Actions.doNothing<KotlinTestToolchainParameters>())
    }

    override fun useKotlinTest(version: String) {
        useKotlinTest(this.providerFactory.provider<String>(Callable { version }))
    }

    override fun useKotlinTest(version: Provider<String>) {
        useKotlinTest(Action { parameters: KotlinTestToolchainParameters -> parameters.getKotlinTestVersion().set(version) })
    }

    private fun useKotlinTest(action: Action<KotlinTestToolchainParameters>) {
        setToolchainAndConfigure<KotlinTestToolchainParameters>(KotlinTestTestToolchain::class.java, Action { parameters: KotlinTestToolchainParameters ->
            parameters.getKotlinTestVersion().convention(KotlinTestTestToolchain.Companion.DEFAULT_VERSION)
            action.execute(parameters)
        })
    }

    override fun useTestNG() {
        useTestNG(Actions.doNothing<TestNGToolchainParameters>())
    }

    override fun useTestNG(version: String) {
        useTestNG(this.providerFactory.provider<String>(Callable { version }))
    }

    override fun useTestNG(version: Provider<String>) {
        useTestNG(Action { parameters: TestNGToolchainParameters -> parameters.getVersion().set(version) })
    }

    private fun useTestNG(action: Action<TestNGToolchainParameters>) {
        setToolchainAndConfigure<TestNGToolchainParameters>(TestNGTestToolchain::class.java, Action { parameters: TestNGToolchainParameters ->
            parameters.getVersion().convention(TestNGTestToolchain.Companion.DEFAULT_VERSION)
            action.execute(parameters)
        })
    }

    private fun <T : JvmTestToolchainParameters?> setToolchainAndConfigure(toolchainType: Class<out JvmTestToolchain<T?>>, action: Action<T?>) {
        val toolchain: JvmTestToolchain<T?> = toolchainFactory.getOrCreate(toolchainType)
        this.testToolchain.set(toolchain)
        action.execute(toolchain.getParameters())
    }

    override fun dependencies(action: Action<in JvmComponentDependencies>) {
        action.execute(getDependencies())
    }

    override fun getBuildDependencies(): TaskDependency {
        return taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            getTargets()!!.forEach(Consumer { dependency: JvmTestSuiteTarget? -> context!!.add(dependency) })
        })
    }

    /**
     * Creates and caches toolchains.  This allows multiple calls to the same use* methods to operate on the same toolchain and maintain the same
     * test framework options instances.
     */
    private class ToolchainFactory(private val objectFactory: ObjectFactory, private val parentServices: ServiceLookup, private val instantiatorFactory: InstantiatorFactory) {
        private val cache: MutableMap<Class<out JvmTestToolchain<*>>, JvmTestToolchain<*>> = HashMap<Class<out JvmTestToolchain<*>>, JvmTestToolchain<*>>()

        fun <T : JvmTestToolchain<*>?> getOrCreate(type: Class<T?>): T? {
            return uncheckedCast<T?>(cache.computeIfAbsent(type) { t: Class<out JvmTestToolchain<*>?>? ->
                create<JvmTestToolchainParameters>(
                    uncheckedCast<Class<out JvmTestToolchain<JvmTestToolchainParameters>>?>(
                        type
                    )
                )
            })
        }

        fun <T : JvmTestToolchainParameters?> create(type: Class<out JvmTestToolchain<T?>>): JvmTestToolchain<T?> {
            val isolationScheme = IsolationScheme<JvmTestToolchain<*>, JvmTestToolchainParameters>(
                uncheckedCast<Class<JvmTestToolchain<*>>?>(JvmTestToolchain::class.java)!!,
                JvmTestToolchainParameters::class.java,
                JvmTestToolchainParameters.None::class.java
            )
            val parametersType: Class<T?> = isolationScheme.parameterTypeFor(type)
            val parameters = isolationScheme.instantiateParameters<T?>(parametersType, Function { type: Class<T?>? -> objectFactory.newInstance(type) })
            val lookup = isolationScheme.servicesForImplementation(parameters!!, parentServices, mutableSetOf<Class<DependencyFactory>>(DependencyFactory::class.java))
            return FrameworkCachingJvmTestToolchain<T?>(instantiatorFactory.decorate(lookup).newInstance(type))
        }
    }
}
