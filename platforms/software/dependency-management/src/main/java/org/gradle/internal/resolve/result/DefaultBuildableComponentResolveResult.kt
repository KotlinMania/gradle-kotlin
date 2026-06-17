/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException

class DefaultBuildableComponentResolveResult : DefaultResourceAwareResolveResult(), BuildableComponentResolveResult {
    private var state: ComponentGraphResolveState? = null
    private var graphState: ComponentGraphSpecificResolveState? = null
    private var failure: ModuleVersionResolveException? = null

    override fun failed(failure: ModuleVersionResolveException?): DefaultBuildableComponentResolveResult {
        state = null
        this.failure = failure
        return this
    }

    override fun notFound(versionIdentifier: ModuleComponentIdentifier) {
        failed(ModuleVersionNotFoundException(DefaultModuleVersionIdentifier.newId(versionIdentifier), getAttempted()))
    }

    override fun resolved(state: ComponentGraphResolveState?, graphState: ComponentGraphSpecificResolveState?) {
        this.state = state
        this.graphState = graphState
    }

    override fun setResult(state: ComponentGraphResolveState?) {
        assertResolved()
        this.state = state
    }

    override fun getId(): ComponentIdentifier {
        assertResolved()
        return state!!.getId()
    }

    @Throws(ModuleVersionResolveException::class)
    override fun getModuleVersionId(): ModuleVersionIdentifier {
        assertResolved()
        return state!!.getMetadata().getModuleVersionId()
    }

    @Throws(ModuleVersionResolveException::class)
    override fun getState(): ComponentGraphResolveState? {
        assertResolved()
        return state
    }

    @Throws(ModuleVersionResolveException::class)
    override fun getGraphState(): ComponentGraphSpecificResolveState? {
        assertResolved()
        return graphState
    }

    override fun getFailure(): ModuleVersionResolveException? {
        assertHasResult()
        return failure
    }

    private fun assertResolved() {
        assertHasResult()
        if (failure != null) {
            throw failure
        }
    }

    private fun assertHasResult() {
        check(hasResult()) { "No result has been specified." }
    }

    override fun hasResult(): Boolean {
        return failure != null || state != null
    }

    fun applyTo(idResolve: BuildableComponentIdResolveResult) {
        super.applyTo(idResolve)
        if (failure != null) {
            idResolve.failed(failure)
        }
        if (state != null) {
            idResolve.resolved(state, graphState)
        }
    }

    override fun applyTo(target: BuildableComponentResolveResult) {
        super.applyTo(target)
        if (failure != null) {
            target.failed(failure)
        }
        if (state != null) {
            target.resolved(state, graphState)
        }
    }
}
