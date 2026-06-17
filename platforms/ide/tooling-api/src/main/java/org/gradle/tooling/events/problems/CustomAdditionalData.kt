/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.events.problems

import org.gradle.api.Incubating

/**
 * Custom Additional data for a problem.
 *
 *
 * This class allows to access additional data via a custom type that was attached to a problem by using [org.gradle.api.problems.ProblemSpec.additionalData].
 *
 * @since 8.13
 */
@Incubating
interface CustomAdditionalData : AdditionalData {
    /**
     * Returns an instance of the given type that accesses the additional data.
     *
     * @param viewType the view type of the additional data
     *
     *
     * This represents the interface or class through which you want to access
     * the underlying data. The system will return an implementation of this type
     * that provides a specific "view" of the data, allowing for type-safe access
     * to underlying data. The view type must be compatible with the actual data structure.
     *
     *
     * **Limitations of the view type:**
     *
     *  * **Allowed types:** Only specific types are supported:
     *
     *  * Simple types: [String], primitives and their wrappers ([Integer], [Boolean], etc.)
     *  * Collections: [java.util.List], [java.util.Set], [java.util.Map]
     *  * Composites: Types composed of the above allowed types
     *
     *
     *  * **Provider API mapping (Provider API types are not allowed in view types):** Provider API types (such as [org.gradle.api.provider.Property])
     * can be mapped to their corresponding allowed types, e.g. `Property<String> getName()` can be represented as `String getName()`
     *
     * @since 8.13
     */
    fun <T> get(viewType: Class<T?>): T?
}
