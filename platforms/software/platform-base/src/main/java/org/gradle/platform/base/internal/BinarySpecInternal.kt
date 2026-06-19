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
package org.gradle.platform.base.internal

import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec

interface BinarySpecInternal : BinarySpec {
    /**
     * The unique identifier of this binary.
     */
    val id: LibraryBinaryIdentifier?

    val component: ComponentSpec?

    /**
     * Returns a name for this binary that is unique for all binaries in the current project.
     */
    val projectScopedName: String?

    val publicType: Class<out BinarySpec?>?

    fun setBuildable(buildable: Boolean)

    val buildAbility: BinaryBuildAbility?
    val isLegacyBinary: Boolean
    var namingScheme: BinaryNamingScheme?

    fun hasCodependentSources(): Boolean
}
