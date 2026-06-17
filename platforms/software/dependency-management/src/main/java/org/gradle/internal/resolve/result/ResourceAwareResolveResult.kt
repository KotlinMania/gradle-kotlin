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

import org.gradle.internal.resource.ExternalResourceName

interface ResourceAwareResolveResult {
    @JvmField
    val attempted: MutableList<String?>?

    /**
     * Adds a location that was used to build this result. This is used for diagnostic messages and logging.
     */
    fun attempted(locationDescription: String?)

    /**
     * Adds a location that was used to build this result. This is used for diagnostic messages and logging.
     */
    fun attempted(location: ExternalResourceName?)

    /**
     * Copies the locations for this result to the given target.
     */
    fun applyTo(target: ResourceAwareResolveResult?)
}
