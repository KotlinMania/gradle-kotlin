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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.internal.tasks.TaskDependencyResolveContext

class CompositeResolvedArtifactSet private constructor(private val sets: MutableList<ResolvedArtifactSet>) : ResolvedArtifactSet {
    override fun visit(visitor: ResolvedArtifactSet.Visitor) {
        for (set in sets) {
            set.visit(visitor)
        }
    }

    override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
        for (set in sets) {
            set.visitTransformSources(visitor)
        }
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact?>) {
        for (set in sets) {
            set.visitExternalArtifacts(visitor)
        }
    }

    override fun visitDependencies(context: TaskDependencyResolveContext?) {
        for (set in sets) {
            set.visitDependencies(context)
        }
    }

    companion object {
        fun of(sets: MutableCollection<out ResolvedArtifactSet?>): ResolvedArtifactSet? {
            val filtered: MutableList<ResolvedArtifactSet> = ArrayList<ResolvedArtifactSet>(sets.size)
            for (set in sets) {
                if (set is CompositeResolvedArtifactSet) {
                    filtered.addAll(set.sets)
                } else if (set !== ResolvedArtifactSet.EMPTY) {
                    filtered.add(set!!)
                }
            }

            if (filtered.isEmpty()) {
                return ResolvedArtifactSet.EMPTY
            }

            if (filtered.size == 1) {
                return filtered.get(0)
            }

            return CompositeResolvedArtifactSet(filtered)
        }

        fun reverse(artifacts: ResolvedArtifactSet?): ResolvedArtifactSet? {
            if (artifacts === ResolvedArtifactSet.EMPTY) {
                return artifacts
            } else if (artifacts is CompositeResolvedArtifactSet) {
                return CompositeResolvedArtifactSet(Lists.reverse<ResolvedArtifactSet?>(artifacts.sets))
            } else {
                return artifacts
            }
        }
    }
}
