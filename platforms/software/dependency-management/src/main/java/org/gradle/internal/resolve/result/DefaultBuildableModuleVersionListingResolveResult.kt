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
import org.gradle.internal.resolve.ModuleVersionResolveException

class DefaultBuildableModuleVersionListingResolveResult : DefaultResourceAwareResolveResult(), BuildableModuleVersionListingResolveResult {
    private var state: BuildableModuleVersionListingResolveResult.State? = BuildableModuleVersionListingResolveResult.State.Unknown
    private var failure: ModuleVersionResolveException? = null
    private var versions: MutableSet<String?>? = null
    private var authoritative = false

    private fun reset(state: BuildableModuleVersionListingResolveResult.State?) {
        this.state = state
        versions = null
        failure = null
        authoritative = false
    }

    override fun getState(): BuildableModuleVersionListingResolveResult.State? {
        return state
    }

    override fun hasResult(): Boolean {
        return state != BuildableModuleVersionListingResolveResult.State.Unknown
    }

    @Throws(ModuleVersionResolveException::class)
    override fun getVersions(): MutableSet<String?>? {
        assertHasResult()
        return versions
    }

    override fun getFailure(): ModuleVersionResolveException? {
        assertHasResult()
        return failure
    }

    override fun listed(versions: MutableCollection<String?>) {
        reset(BuildableModuleVersionListingResolveResult.State.Listed)
        this.versions = ImmutableSet.copyOf<String?>(versions)
        this.authoritative = true
    }

    override fun failed(failure: ModuleVersionResolveException?) {
        reset(BuildableModuleVersionListingResolveResult.State.Failed)
        this.failure = failure
        this.authoritative = true
    }

    override fun isAuthoritative(): Boolean {
        assertHasResult()
        return authoritative
    }

    override fun setAuthoritative(authoritative: Boolean) {
        assertHasResult()
        this.authoritative = authoritative
    }

    private fun assertHasResult() {
        check(hasResult()) { "No result has been specified." }
    }
}
