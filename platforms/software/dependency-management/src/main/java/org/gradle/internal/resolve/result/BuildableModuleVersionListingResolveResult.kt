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

/**
 * The result of attempting to resolve the list of versions for a particular module.
 */
interface BuildableModuleVersionListingResolveResult : ResourceAwareResolveResult, ErroringResolveResult<ModuleVersionResolveException?> {
    enum class State {
        /**
         * Listing has succeeded.
         */
        Listed,

        /**
         * Listing has failed.
         */
        Failed,

        /**
         * Listing hasn't been performed yet, or another attempt can be made.
         */
        Unknown
    }

    /**
     * Returns the current state of this result.
     */
    @JvmField
    val state: State?

    @JvmField
    @get:Throws(ModuleVersionResolveException::class)
    val versions: MutableSet<String?>?

    override fun getFailure(): ModuleVersionResolveException?

    /**
     * Marks the module as having been listed to have the specified versions available.
     */
    fun listed(versions: MutableCollection<String?>?)

    /**
     * Marks the list as failed with the given exception.
     */
    override fun failed(failure: ModuleVersionResolveException?)

    /**
     * Returns true if the result is from an authoritative source. Defaults to true.
     */
    @JvmField
    var isAuthoritative: Boolean
}
