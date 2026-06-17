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

import org.gradle.internal.resolve.ModuleVersionResolveException
import java.util.function.Function

class DefaultBuildableModuleComponentMetaDataResolveResult<T> : DefaultResourceAwareResolveResult(), BuildableModuleComponentMetaDataResolveResult<T?> {
    private var state: BuildableModuleComponentMetaDataResolveResult.State? = BuildableModuleComponentMetaDataResolveResult.State.Unknown
    private var failure: ModuleVersionResolveException? = null
    private var metaData: T? = null
    private var authoritative = false

    private fun reset(state: BuildableModuleComponentMetaDataResolveResult.State?) {
        this.state = state
        metaData = null
        failure = null
        authoritative = false
    }

    fun reset() {
        reset(BuildableModuleComponentMetaDataResolveResult.State.Unknown)
    }

    override fun resolved(metaData: T?) {
        reset(BuildableModuleComponentMetaDataResolveResult.State.Resolved)
        this.failure = null
        this.metaData = metaData
        authoritative = true
    }

    override fun setMetadata(metaData: T?) {
        assertResolved()
        this.metaData = metaData
    }

    override fun missing() {
        reset(BuildableModuleComponentMetaDataResolveResult.State.Missing)
        this.metaData = null
        this.failure = null
        authoritative = true
    }

    override fun failed(failure: ModuleVersionResolveException?) {
        reset(BuildableModuleComponentMetaDataResolveResult.State.Failed)
        this.metaData = null
        this.failure = failure
        authoritative = true
    }

    override fun getState(): BuildableModuleComponentMetaDataResolveResult.State? {
        return state
    }

    override fun hasResult(): Boolean {
        return state != BuildableModuleComponentMetaDataResolveResult.State.Unknown
    }

    override fun getFailure(): ModuleVersionResolveException? {
        assertHasResult()
        return failure
    }

    @Throws(ModuleVersionResolveException::class)
    override fun getMetaData(): T? {
        assertResolved()
        return metaData
    }

    override fun isAuthoritative(): Boolean {
        assertHasResult()
        return authoritative
    }

    override fun setAuthoritative(authoritative: Boolean) {
        assertHasResult()
        this.authoritative = authoritative
    }

    override fun redirectToGradleMetadata() {
        reset(BuildableModuleComponentMetaDataResolveResult.State.Redirect)
    }

    override fun shouldUseGradleMetatada(): Boolean {
        return state == BuildableModuleComponentMetaDataResolveResult.State.Redirect
    }

    fun <S> applyTo(target: BuildableModuleComponentMetaDataResolveResult<S?>, resultMapper: Function<T?, S?>) {
        if (state == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
            target.resolved(resultMapper.apply(metaData))
        } else if (state == BuildableModuleComponentMetaDataResolveResult.State.Failed) {
            target.failed(failure)
        } else if (state == BuildableModuleComponentMetaDataResolveResult.State.Redirect) {
            target.redirectToGradleMetadata()
        } else if (state == BuildableModuleComponentMetaDataResolveResult.State.Missing) {
            target.missing()
        } else {
            throw IllegalStateException()
        }
        target.setAuthoritative(authoritative)
        applyTo(target)
    }

    private fun assertHasResult() {
        check(hasResult()) { "No result has been specified." }
    }

    private fun assertResolved() {
        if (state == BuildableModuleComponentMetaDataResolveResult.State.Failed) {
            throw failure
        }
        check(state == BuildableModuleComponentMetaDataResolveResult.State.Resolved) { "This module has not been resolved." }
    }
}
