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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ListMultimap
import org.apache.commons.lang3.ObjectUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.internal.operations.BuildOperationExecutor
import java.util.TreeSet

class DefaultResolvedDependency(
    private val variantName: String,
    private val moduleVersionId: ModuleVersionIdentifier,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val resolutionHost: ResolutionHost
) : ResolvedDependency {
    private val children: MutableSet<DefaultResolvedDependency?> = LinkedHashSet<DefaultResolvedDependency?>()
    private val parents: MutableSet<ResolvedDependency?> = LinkedHashSet<ResolvedDependency?>()
    private val parentArtifacts: ListMultimap<ResolvedDependency?, ResolvedArtifactSet?> = ArrayListMultimap.create<ResolvedDependency?, ResolvedArtifactSet?>()
    private val moduleArtifacts: MutableSet<ResolvedArtifactSet?>
    private val allArtifactsCache: MutableMap<ResolvedDependency?, MutableSet<ResolvedArtifact?>> = HashMap<ResolvedDependency?, MutableSet<ResolvedArtifact?>>()
    private var allModuleArtifactsCache: MutableSet<ResolvedArtifact?>? = null

    init {
        this.moduleArtifacts = LinkedHashSet<ResolvedArtifactSet?>()
    }

    override fun getName(): String {
        return String.format("%s:%s:%s", moduleVersionId.getGroup(), moduleVersionId.getName(), moduleVersionId.getVersion())
    }

    override fun getModuleGroup(): String {
        return moduleVersionId.getGroup()
    }

    override fun getModuleName(): String {
        return moduleVersionId.getName()
    }

    override fun getModuleVersion(): String {
        return moduleVersionId.getVersion()
    }

    override fun getConfiguration(): String {
        return variantName
    }

    override fun getModule(): ResolvedModuleVersion {
        return DefaultResolvedModuleVersion(moduleVersionId)
    }

    override fun getChildren(): ImmutableSet<ResolvedDependency> {
        return ImmutableSet.copyOf<ResolvedDependency?>(children)
    }

    override fun getModuleArtifacts(): MutableSet<ResolvedArtifact?> {
        return sort(CompositeResolvedArtifactSet.of(moduleArtifacts))
    }

    override fun getAllModuleArtifacts(): MutableSet<ResolvedArtifact?> {
        if (allModuleArtifactsCache == null) {
            val allArtifacts: MutableSet<ResolvedArtifact?> = LinkedHashSet<ResolvedArtifact?>(getModuleArtifacts())
            for (childResolvedDependency in getChildren()) {
                allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts())
            }
            allModuleArtifactsCache = allArtifacts
        }
        return allModuleArtifactsCache!!
    }

    override fun getParentArtifacts(parent: ResolvedDependency): MutableSet<ResolvedArtifact?> {
        return sort(getArtifactsForIncomingEdge(parent))
    }

    private fun sort(artifacts: ResolvedArtifactSet): MutableSet<ResolvedArtifact?> {
        val visitor = ArtifactCollectingVisitor(TreeSet<ResolvedArtifact?>(ResolvedArtifactComparator()))
        ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(visitor)
        if (!visitor.getFailures().isEmpty()) {
            resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures())
        }
        return visitor.getArtifacts()
    }

    private fun getArtifactsForIncomingEdge(parent: ResolvedDependency?): ResolvedArtifactSet {
        if (!parents.contains(parent)) {
            throw InvalidUserDataException("Provided dependency (" + parent + ") must be a parent of: " + this)
        }
        return CompositeResolvedArtifactSet.of(parentArtifacts.get(parent))
    }

    override fun getArtifacts(parent: ResolvedDependency): MutableSet<ResolvedArtifact?> {
        return getParentArtifacts(parent)
    }

    override fun getAllArtifacts(parent: ResolvedDependency): MutableSet<ResolvedArtifact?> {
        if (allArtifactsCache.get(parent) == null) {
            val allArtifacts: MutableSet<ResolvedArtifact?> = LinkedHashSet<ResolvedArtifact?>(getArtifacts(parent))
            for (childResolvedDependency in getChildren()) {
                for (childParent in childResolvedDependency.getParents()) {
                    allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent))
                }
            }
            allArtifactsCache.put(parent, allArtifacts)
        }
        return allArtifactsCache.get(parent)!!
    }

    override fun getParents(): MutableSet<ResolvedDependency?> {
        return parents
    }

    override fun toString(): String {
        return getName() + ";" + getConfiguration()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultResolvedDependency
        return variantName == that.variantName &&
                moduleVersionId == that.moduleVersionId
    }

    override fun hashCode(): Int {
        return variantName.hashCode() xor moduleVersionId.hashCode()
    }

    fun addChild(child: DefaultResolvedDependency) {
        children.add(child)
        child.parents.add(this)
    }

    fun addParentSpecificArtifacts(parent: ResolvedDependency?, artifacts: ResolvedArtifactSet?) {
        this.parentArtifacts.put(parent, artifacts)
        moduleArtifacts.add(artifacts)
    }

    fun addModuleArtifacts(artifacts: ResolvedArtifactSet?) {
        moduleArtifacts.add(artifacts)
    }

    private class ResolvedArtifactComparator : Comparator<ResolvedArtifact?> {
        override fun compare(artifact1: ResolvedArtifact, artifact2: ResolvedArtifact): Int {
            var diff = artifact1.getName().compareTo(artifact2.getName())
            if (diff != 0) {
                return diff
            }
            diff = ObjectUtils.compare<String?>(artifact1.getClassifier(), artifact2.getClassifier())
            if (diff != 0) {
                return diff
            }
            diff = ObjectUtils.compare<String?>(artifact1.getExtension(), artifact2.getExtension())
            if (diff != 0) {
                return diff
            }
            diff = artifact1.getType().compareTo(artifact2.getType())
            if (diff != 0) {
                return diff
            }
            // Use an arbitrary ordering when the artifacts have the same public attributes
            return Integer.compare(artifact1.hashCode(), artifact2.hashCode())
        }
    }
}
