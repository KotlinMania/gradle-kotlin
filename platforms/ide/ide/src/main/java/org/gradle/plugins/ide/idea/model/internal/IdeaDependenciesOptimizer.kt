/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.idea.model.internal

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import org.gradle.plugins.ide.idea.model.Dependency
import org.gradle.plugins.ide.idea.model.ModuleDependency
import org.gradle.plugins.ide.idea.model.ModuleLibrary
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary

/**
 * Minimizes a set of IDEA dependencies based on knowledge about how IDEA handles compilation and runtime of main and test classes:
 *
 *
 *  *  COMPILE dependencies are visible everywhere.
 *  *  PROVIDED dependencies are visible both when compiling main and test code as well as when running tests (but not when running main).
 *  *  RUNTIME dependencies are visible when running main and test code.
 *  *  TEST dependencies are visible when compiling and running tests.
 *
 *
 * This means we can do the following simplifications:
 *
 *
 *  * If a dependency is in COMPILE, we can remove it everywhere else.
 *  * If a dependency is PROVIDED, we don't need it in TEST.
 *  * If a dependency is in RUNTIME and PROVIDED, we can hoist it up to COMPILE.
 *
 *
 * This results is much closer to what a user would do by hand. Having less dependencies also makes IntelliJ faster.
 */
internal class IdeaDependenciesOptimizer {
    fun optimizeDeps(deps: MutableCollection<Dependency>) {
        val scopesByDependencyKey = collectScopesByDependency(deps)
        optimizeScopes(scopesByDependencyKey)
        applyScopesToDependencies(deps, scopesByDependencyKey)
    }

    private fun collectScopesByDependency(deps: MutableCollection<Dependency>): Multimap<Any?, GeneratedIdeaScope?> {
        val scopesByDependencyKey: Multimap<Any?, GeneratedIdeaScope?> =
            MultimapBuilder.hashKeys().enumSetValues<GeneratedIdeaScope?>(GeneratedIdeaScope::class.java).build<Any?, GeneratedIdeaScope?>()
        for (dep in deps) {
            scopesByDependencyKey.put(getKey(dep), GeneratedIdeaScope.Companion.nullSafeValueOf(dep.getScope()))
        }
        return scopesByDependencyKey
    }

    private fun optimizeScopes(scopesByDependencyKey: Multimap<Any?, GeneratedIdeaScope?>) {
        for (entry in scopesByDependencyKey.asMap().entries) {
            optimizeScopes(entry.value)
        }
    }

    private fun applyScopesToDependencies(deps: MutableCollection<Dependency>, scopesByDependencyKey: Multimap<Any?, GeneratedIdeaScope?>) {
        val iterator = deps.iterator()
        while (iterator.hasNext()) {
            applyScopeToNextDependency(iterator, scopesByDependencyKey)
        }
    }

    private fun applyScopeToNextDependency(iterator: MutableIterator<Dependency>, scopesByDependencyKey: Multimap<Any?, GeneratedIdeaScope?>) {
        val dep = iterator.next()
        val key = getKey(dep)
        val ideaScopes: MutableCollection<GeneratedIdeaScope> = scopesByDependencyKey.get(key)
        if (ideaScopes.isEmpty()) {
            iterator.remove()
        } else {
            val scope = ideaScopes.iterator().next()
            dep.setScope(scope.name)
            scopesByDependencyKey.remove(key, scope)
        }
    }

    private fun getKey(dep: Dependency): Any? {
        if (dep is ModuleDependency) {
            return dep.getName()
        } else if (dep is SingleEntryModuleLibrary) {
            return dep.getLibraryFile()
        } else if (dep is ModuleLibrary) {
            return dep.getClasses()
        } else {
            throw IllegalArgumentException("Unsupported type: " + dep.javaClass.getName())
        }
    }

    private fun optimizeScopes(ideaScopes: MutableCollection<GeneratedIdeaScope?>) {
        val isRuntime = ideaScopes.contains(GeneratedIdeaScope.RUNTIME)
        val isProvided = ideaScopes.contains(GeneratedIdeaScope.PROVIDED)
        var isCompile = ideaScopes.contains(GeneratedIdeaScope.COMPILE)

        if (isProvided) {
            ideaScopes.remove(GeneratedIdeaScope.TEST)
        }

        if (isRuntime && isProvided) {
            ideaScopes.add(GeneratedIdeaScope.COMPILE)
            isCompile = true
        }

        if (isCompile) {
            ideaScopes.remove(GeneratedIdeaScope.TEST)
            ideaScopes.remove(GeneratedIdeaScope.RUNTIME)
            ideaScopes.remove(GeneratedIdeaScope.PROVIDED)
        }
    }
}
