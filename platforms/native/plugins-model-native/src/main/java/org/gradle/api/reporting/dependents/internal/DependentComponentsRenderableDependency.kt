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
package org.gradle.api.reporting.dependents.internal

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.AbstractRenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.internal.ComponentSpecInternal
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult

class DependentComponentsRenderableDependency(id: Any, name: String, description: String, buildable: Boolean, testSuite: Boolean, children: LinkedHashSet<out RenderableDependency>) :
    AbstractRenderableDependency() {
    private val id: Any
    private val name: String
    private val description: String
    val isBuildable: Boolean
    val isTestSuite: Boolean
    private val children: LinkedHashSet<out RenderableDependency>

    init {
        Preconditions.checkNotNull<Any>(id, "id must not be null")
        Preconditions.checkNotNull<String?>(Strings.emptyToNull(name), "name must not be null nor empty")
        this.id = id
        this.name = name
        this.description = Strings.emptyToNull(description)!!
        this.isBuildable = buildable
        this.isTestSuite = testSuite
        this.children = children
    }

    override fun getId(): Any {
        return id
    }

    override fun getName(): String {
        return name
    }

    override fun getDescription(): String {
        return description
    }

    override fun getResolutionState(): RenderableDependency.ResolutionState {
        return RenderableDependency.ResolutionState.RESOLVED
    }

    override fun getChildren(): MutableSet<out RenderableDependency> {
        return children
    }

    companion object {
        @JvmOverloads  //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        fun of(
            componentSpec: ComponentSpec,
            internalProtocol: ComponentSpecInternal,
            children: LinkedHashSet<DependentComponentsRenderableDependency> = LinkedHashSet<DependentComponentsRenderableDependency>()
        ): DependentComponentsRenderableDependency {
            val id = internalProtocol.getIdentifier()
            val name = DependentComponentsUtils.getBuildScopedTerseName(id)
            val description = componentSpec.getDisplayName()
            var buildable = true
            if (componentSpec is VariantComponentSpec) {
                // Consider variant aware components with no buildable binaries as non-buildables
                val variantComponentSpec = componentSpec
                buildable = variantComponentSpec.getBinaries().values().stream().anyMatch { obj: BinarySpec? -> obj!!.isBuildable() }
            }
            return DependentComponentsRenderableDependency(id, name, description, buildable, false, children)
        }

        fun of(resolvedResult: DependentBinariesResolvedResult): DependentComponentsRenderableDependency {
            val id = resolvedResult.getId()
            val name = DependentComponentsUtils.getBuildScopedTerseName(id)
            val description = id.getDisplayName()
            val buildable = resolvedResult.isBuildable()
            val testSuite = resolvedResult.isTestSuite()
            val children = LinkedHashSet<DependentComponentsRenderableDependency>()
            for (childResolutionResult in resolvedResult.getChildren()) {
                children.add(of(childResolutionResult))
            }
            return DependentComponentsRenderableDependency(id, name, description, buildable, testSuite, children)
        }
    }
}
