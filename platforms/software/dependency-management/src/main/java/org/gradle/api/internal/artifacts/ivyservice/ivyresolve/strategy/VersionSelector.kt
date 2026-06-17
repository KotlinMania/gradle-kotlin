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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.artifacts.ComponentMetadata

/**
 * Implementations of version selector are expected to be immutable and thread-safe.
 */
interface VersionSelector {
    /**
     * Indicates if the given version selector is dynamic.
     */
    @JvmField
    val isDynamic: Boolean

    /**
     * Indicates if module metadata is required to determine if the
     * selector matches a candidate version.
     */
    fun requiresMetadata(): Boolean

    /**
     * Indicates if the selector implies that it matches only a single version.
     */
    fun matchesUniqueVersion(): Boolean

    /**
     * Indicates if the selector matches the given candidate version.
     * Only called if [.requiresMetadata] returned `false`.
     *
     * @param candidate the candidate version
     */
    fun accept(candidate: String?): Boolean

    /**
     * Indicates if the selector matches the given candidate version.
     * Only called if [.requiresMetadata] returned `false`.
     *
     * @param candidate the candidate version
     */
    fun accept(candidate: Version?): Boolean

    /**
     * Indicates if the selector matches the given candidate version
     * (whose metadata is provided). May also be called if [.isDynamic] returned
     * `false`, in which case it should return the same result as
     * `accept(candidate.getId().getVersion()`.
     *
     * @param candidate the metadata for the candidate version
     */
    fun accept(candidate: ComponentMetadata?): Boolean

    /**
     * Indicates if a version selector can be used to short-circuit selection, whenever a different
     * version has been selected previously. Typically, an exact version selector can short-circuit,
     * in the sense that it will always return a correct answer whenever exposed to a version. But a
     * "latest" selector will give a different answer whether is called first, or starting with a
     * pre-selected version.
     *
     * @return true if this selector can short-circuit
     */
    fun canShortCircuitWhenVersionAlreadyPreselected(): Boolean

    /**
     * Returns this selector as a string.
     *
     * @return a stringy representation of this selector
     */
    val selector: String?
}
