/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.DisplayName
import java.io.File

class OpaqueComponentArtifactIdentifier(file: File) : ComponentArtifactIdentifier, ComponentIdentifier, DisplayName {
    val file: File

    init {
        this.file = file
    }

    override fun getComponentIdentifier(): ComponentIdentifier {
        return this
    }

    override fun getDisplayName(): String {
        return file.getName()
    }

    override fun getCapitalizedDisplayName(): String {
        return getDisplayName()
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun equals(obj: Any): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }
        val other = obj as OpaqueComponentArtifactIdentifier
        return file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}
