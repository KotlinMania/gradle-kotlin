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

import org.gradle.internal.resolve.ModuleVersionResolveException

/**
 * The result of resolving a module version selector to a particular component id.
 * The result may optionally include the graph resolution state for the selected component, if it is cheaply available (for example, it was used to select the component).
 */
interface ComponentIdResolveResult : ResolveResult {
    /**
     * Returns the resolve failure, if any.
     */
    override fun getFailure(): ModuleVersionResolveException?

    /**
     * Returns the identifier of the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    @JvmField
    val id: ComponentIdentifier?

    /**
     * Returns the module version id of the component.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the id is unknown.
     */
    @JvmField
    val moduleVersionId: ModuleVersionIdentifier?

    /**
     * Returns the graph resolution state for the component, if it was available at resolve time.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    @JvmField
    val state: ComponentGraphResolveState?

    /**
     * Returns the graph specific resolution state for the component, if it was available at resolve time.
     *
     * @throws ModuleVersionResolveException If resolution was unsuccessful and the descriptor is not available.
     */
    @JvmField
    val graphState: ComponentGraphSpecificResolveState?

    /**
     * Returns true if the component id was resolved, but it was rejected by constraint.
     */
    @JvmField
    val isRejected: Boolean

    /**
     * @return the set of unmatched versions, that is to say versions which were listed but didn't match the selector
     */
    @JvmField
    val unmatchedVersions: MutableSet<String?>?

    /**
     * @return the list of versions which were considered for this module but rejected.
     */
    @JvmField
    val rejectedVersions: MutableCollection<RejectedVersion?>?

    /**
     * Tags this resolve result, for visiting. This is a performance optimization. It will return
     * true if the last tagged object is different, false otherwise. This is meant to replace the
     * use of a hash set to collect the visited items.
     */
    fun mark(o: Any?): Boolean
}
