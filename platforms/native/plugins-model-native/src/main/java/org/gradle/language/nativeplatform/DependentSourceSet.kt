/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform

import org.gradle.api.Incubating
import org.gradle.internal.HasInternalProtocol
import org.gradle.language.base.LanguageSourceSet

/**
 * A source set that depends on one or more [org.gradle.nativeplatform.NativeDependencySet]s to be built.
 */
@Incubating
@HasInternalProtocol
interface DependentSourceSet : LanguageSourceSet {
    /**
     * The libraries that this source set requires.
     */
    @JvmField
    val libs: MutableCollection<*>?

    /**
     * Adds a library that this source set requires. This method accepts the following types:
     *
     *
     *  * A [org.gradle.nativeplatform.NativeLibrarySpec]
     *  * A [org.gradle.nativeplatform.NativeDependencySet]
     *  * A [LanguageSourceSet]
     *  * A [java.util.Map] containing the library selector.
     *
     *
     * The Map notation supports the following String attributes:
     *
     *
     *  * project: the path to the project containing the library (optional, defaults to current project)
     *  * library: the name of the library (required)
     *  * linkage: the library linkage required ['shared'/'static'] (optional, defaults to 'shared')
     *
     */
    fun lib(library: Any?)

    /**
     * Returns the pre-compiled header configured for this source set.
     *
     * @return the pre-compiled header
     */
    /**
     * Sets the pre-compiled header to be used when compiling sources in this source set.
     *
     * @param header the header to precompile
     */
    @JvmField
    var preCompiledHeader: String?
}
