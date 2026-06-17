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
package org.gradle.internal.resolve.result

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.RejectedVersion

class DefaultBuildableComponentIdResolveResult : DefaultResourceAwareResolveResult(), BuildableComponentIdResolveResult {
    private var failure: ModuleVersionResolveException? = null
    private var state: ComponentGraphResolveState? = null
    private var graphState: ComponentGraphSpecificResolveState? = null
    private var id: ComponentIdentifier? = null
    private var moduleVersionId: ModuleVersionIdentifier? = null
    private var rejected = false
    private var unmatchedVersions: ImmutableSet.Builder<String?>? = null
    private var rejections: ImmutableSet.Builder<RejectedVersion?>? = null
    private var mark: Any? = null

    override fun hasResult(): Boolean {
        return id != null || failure != null
    }

    override fun getFailure(): ModuleVersionResolveException? {
        return failure
    }

    override fun getId(): ComponentIdentifier? {
        assertResolved()
        return id
    }

    override fun getModuleVersionId(): ModuleVersionIdentifier? {
        assertResolved()
        return moduleVersionId
    }

    override fun getState(): ComponentGraphResolveState? {
        assertResolved()
        return state
    }

    override fun getGraphState(): ComponentGraphSpecificResolveState? {
        assertResolved()
        return graphState
    }

    override fun isRejected(): Boolean {
        return rejected
    }

    override fun resolved(id: ComponentIdentifier?, moduleVersionIdentifier: ModuleVersionIdentifier?) {
        reset()
        this.id = id
        this.moduleVersionId = moduleVersionIdentifier
    }

    override fun rejected(id: ComponentIdentifier?, moduleVersionIdentifier: ModuleVersionIdentifier?) {
        resolved(id, moduleVersionIdentifier)
        rejected = true
    }

    override fun resolved(state: ComponentGraphResolveState, graphState: ComponentGraphSpecificResolveState?) {
        resolved(state.getId(), state.getMetadata().getModuleVersionId())
        this.state = state
        this.graphState = graphState
    }

    override fun failed(failure: ModuleVersionResolveException?) {
        reset()
        this.failure = failure
    }

    override fun unmatched(unmatchedVersions: MutableCollection<String?>) {
        if (unmatchedVersions.isEmpty()) {
            return
        }
        if (this.unmatchedVersions == null) {
            this.unmatchedVersions = ImmutableSet.Builder<String?>()
        }
        this.unmatchedVersions!!.addAll(unmatchedVersions)
    }

    override fun rejections(rejections: MutableCollection<RejectedVersion?>) {
        if (rejections.isEmpty()) {
            return
        }
        if (this.rejections == null) {
            this.rejections = ImmutableSet.Builder<RejectedVersion?>()
        }
        this.rejections!!.addAll(rejections)
    }

    override fun getUnmatchedVersions(): MutableSet<String?> {
        return safeBuild<String?>(unmatchedVersions)
    }

    override fun getRejectedVersions(): MutableCollection<RejectedVersion?> {
        return safeBuild<RejectedVersion?>(rejections)
    }

    override fun mark(o: Any?): Boolean {
        if (mark === o) {
            return false
        }
        mark = o
        return true
    }

    private fun assertResolved() {
        if (failure != null) {
            throw failure
        }
        checkNotNull(id) { "Not resolved." }
    }

    private fun reset() {
        failure = null
        state = null
        id = null
        moduleVersionId = null
        rejected = false
    }

    companion object {
        private fun <T> safeBuild(builder: ImmutableSet.Builder<T?>?): MutableSet<T?> {
            if (builder == null) {
                return mutableSetOf<T?>()
            }
            return builder.build()
        }
    }
}
