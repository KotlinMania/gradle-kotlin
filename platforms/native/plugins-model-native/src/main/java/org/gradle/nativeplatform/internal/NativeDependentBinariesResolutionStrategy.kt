/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.internal

import com.google.common.base.Preconditions
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Ordering
import org.gradle.api.CircularReferenceException
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.api.reporting.dependents.internal.DependentComponentsUtils.getBuildScopedTerseName
import org.gradle.internal.build.AllProjectsAccess
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.graph.DirectedGraph
import org.gradle.internal.graph.DirectedGraphRenderer
import org.gradle.internal.graph.GraphNodeRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.ModelMap
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.dependents.AbstractDependentBinariesResolutionStrategy
import org.gradle.platform.base.internal.dependents.DefaultDependentBinariesResolvedResult
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult
import java.io.StringWriter
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class NativeDependentBinariesResolutionStrategy(projectRegistry: BuildProjectRegistry, projectModelResolver: ProjectModelResolver) : AbstractDependentBinariesResolutionStrategy() {
    interface TestSupport {
        fun isTestSuite(target: BinarySpecInternal?): Boolean

        fun getTestDependencies(nativeBinary: NativeBinarySpecInternal?): MutableList<NativeBinarySpecInternal?>?
    }

    private class State {
        private val dependencies: MutableMap<NativeBinarySpecInternal, MutableSet<NativeBinarySpecInternal?>?> = LinkedHashMap<NativeBinarySpecInternal, MutableSet<NativeBinarySpecInternal?>?>()
        private val dependents: MutableMap<NativeBinarySpecInternal?, MutableList<NativeBinarySpecInternal>?> = HashMap<NativeBinarySpecInternal?, MutableList<NativeBinarySpecInternal>?>()

        fun registerBinary(binary: NativeBinarySpecInternal?) {
            if (dependencies.get(binary) == null) {
                dependencies.put(binary!!, LinkedHashSet<NativeBinarySpecInternal?>())
            }
        }

        fun getDependents(target: NativeBinarySpecInternal?): MutableList<NativeBinarySpecInternal> {
            var result = dependents.get(target)
            if (result == null) {
                result = ArrayList<NativeBinarySpecInternal>()
                for (dependentBinary in dependencies.keys) {
                    if (dependencies.get(dependentBinary)!!.contains(target)) {
                        result.add(dependentBinary)
                    }
                }
                dependents.put(target, result)
            }
            return result
        }
    }

    private val projectRegistry: BuildProjectRegistry
    private val projectModelResolver: ProjectModelResolver
    private val stateCache: Cache<String?, State> = CacheBuilder.newBuilder()
        .maximumSize(1)
        .expireAfterAccess(10, TimeUnit.SECONDS)
        .build<String?, State?>()
    private val resultsCache = CacheBuilder.newBuilder()
        .maximumSize(3000)
        .expireAfterAccess(10, TimeUnit.SECONDS)
        .build<NativeBinarySpecInternal?, MutableList<DependentBinariesResolvedResult?>?>()

    private var testSupport: TestSupport? = null

    init {
        Preconditions.checkNotNull<BuildProjectRegistry?>(projectRegistry, "ProjectRegistry must not be null")
        Preconditions.checkNotNull<ProjectModelResolver?>(projectModelResolver, "ProjectModelResolver must not be null")
        this.projectRegistry = projectRegistry
        this.projectModelResolver = projectModelResolver
    }

    override fun getName(): String {
        return NAME
    }

    fun setTestSupport(testSupport: TestSupport?) {
        this.testSupport = testSupport
    }

    override fun resolveDependents(target: BinarySpecInternal?): MutableList<DependentBinariesResolvedResult?>? {
        if (target !is NativeBinarySpecInternal) {
            return null
        }
        return resolveDependentBinaries(target)
    }

    private fun resolveDependentBinaries(target: NativeBinarySpecInternal): MutableList<DependentBinariesResolvedResult?> {
        val state = this.state
        return buildResolvedResult(target, state)
    }

    private val state: State
        get() {
            try {
                return stateCache.get("state", object : Callable<State?> {
                    override fun call(): State {
                        return buildState()
                    }
                })
            } catch (ex: ExecutionException) {
                throw RuntimeException("Unable to build native dependent binaries resolution cache", ex)
            }
        }

    private fun buildState(): State {
        val state = State()

        projectRegistry.applyToMutableStateOfAllProjects(Consumer { access: AllProjectsAccess? ->
            val orderedProjects = Ordering.usingToString().sortedCopy(projectRegistry.getAllProjects())
            for (projectState in orderedProjects) {
                if (access!!.getMutableModel(projectState).getPlugins().hasPlugin(ComponentModelBasePlugin::class.java)) {
                    val modelRegistry = projectModelResolver.resolveProjectModel(projectState.getProjectPath().toString())
                    val components = modelRegistry!!.realize<ModelMap<NativeComponentSpec?>>("components", ModelTypes.modelMap<NativeComponentSpec?>(NativeComponentSpec::class.java))
                    for (binary in allBinariesOf(components.withType<VariantComponentSpec?>(VariantComponentSpec::class.java))) {
                        state.registerBinary(binary)
                    }
                    val testSuites = modelRegistry.find<ModelMap<Any?>?>("testSuites", ModelTypes.modelMap<Any?>(Any::class.java))
                    if (testSuites != null) {
                        for (binary in allBinariesOf(testSuites.withType<NativeComponentSpec?>(NativeComponentSpec::class.java).withType<VariantComponentSpec?>(VariantComponentSpec::class.java))) {
                            state.registerBinary(binary)
                        }
                    }
                }
            }
        })

        for (nativeBinary in state.dependencies.keys) {
            for (libraryBinary in nativeBinary.getDependentBinaries()) {
                // Skip prebuilt libraries
                if (libraryBinary is NativeBinarySpecInternal) {
                    // Unfortunate cast! see LibraryBinaryLocator
                    state.dependencies.get(nativeBinary)!!.add(libraryBinary as NativeBinarySpecInternal)
                }
            }
            if (testSupport != null) {
                state.dependencies.get(nativeBinary)!!.addAll(testSupport!!.getTestDependencies(nativeBinary)!!)
            }
        }

        return state
    }

    override fun isTestSuite(target: BinarySpecInternal?): Boolean {
        return testSupport != null && testSupport!!.isTestSuite(target)
    }

    private fun allBinariesOf(components: ModelMap<VariantComponentSpec>): MutableList<NativeBinarySpecInternal?> {
        val binaries: MutableList<NativeBinarySpecInternal?> = ArrayList<NativeBinarySpecInternal?>()
        for (nativeComponent in components) {
            for (nativeBinary in nativeComponent.getBinaries().withType<NativeBinarySpecInternal?>(NativeBinarySpecInternal::class.java)) {
                binaries.add(nativeBinary)
            }
        }
        return binaries
    }

    private fun buildResolvedResult(target: NativeBinarySpecInternal, state: State): MutableList<DependentBinariesResolvedResult?> {
        val stack: Deque<NativeBinarySpecInternal?> = ArrayDeque<NativeBinarySpecInternal?>()
        return doBuildResolvedResult(target, state, stack)
    }

    private fun doBuildResolvedResult(target: NativeBinarySpecInternal, state: State, stack: Deque<NativeBinarySpecInternal?>): MutableList<DependentBinariesResolvedResult?> {
        if (stack.contains(target)) {
            onCircularDependencies(state, stack, target)
        }
        var result = resultsCache.getIfPresent(target)
        if (result != null) {
            return result
        }
        stack.push(target)
        result = ArrayList<DependentBinariesResolvedResult?>()
        val dependents = state.getDependents(target)
        for (dependent in dependents) {
            val children = doBuildResolvedResult(dependent, state, stack)
            result.add(DefaultDependentBinariesResolvedResult(dependent.id, dependent.projectScopedName, dependent.isBuildable, isTestSuite(dependent), children))
        }
        stack.pop()
        resultsCache.put(target, result)
        return result
    }

    private fun onCircularDependencies(state: State, stack: Deque<NativeBinarySpecInternal?>, target: NativeBinarySpecInternal?) {
        val nodeRenderer: GraphNodeRenderer<NativeBinarySpecInternal?> = object : GraphNodeRenderer<NativeBinarySpecInternal?> {
            override fun renderTo(node: NativeBinarySpecInternal, output: StyledTextOutput, alreadySeen: Boolean) {
                val name = getBuildScopedTerseName(node.id)
                output.withStyle(StyledTextOutput.Style.Identifier)!!.text(name)
            }
        }
        val directedGraph: DirectedGraph<NativeBinarySpecInternal?, Any?> = object : DirectedGraph<NativeBinarySpecInternal?, Any?> {
            override fun getNodeValues(node: NativeBinarySpecInternal?, values: MutableCollection<in Any?>?, connectedNodes: MutableCollection<in NativeBinarySpecInternal?>) {
                for (binary in stack) {
                    if (state.getDependents(node).contains(binary)) {
                        connectedNodes.add(binary)
                    }
                }
            }
        }
        val graphRenderer = DirectedGraphRenderer<NativeBinarySpecInternal?>(nodeRenderer, directedGraph)
        val writer = StringWriter()
        graphRenderer.renderTo(target, writer)
        throw CircularReferenceException(String.format("Circular dependency between the following binaries:%n%s", writer.toString()))
    }

    companion object {
        const val NAME: String = "native"
    }
}
