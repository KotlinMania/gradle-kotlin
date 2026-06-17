/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.internal.component.model.IvyArtifactName

internal class DefaultExcludeNothing private constructor() : ExcludeNothing {
    override fun excludes(module: ModuleIdentifier?): Boolean {
        return false
    }

    override fun excludesArtifact(module: ModuleIdentifier?, artifactName: IvyArtifactName?): Boolean {
        return false
    }

    override fun mayExcludeArtifacts(): Boolean {
        return false
    }

    override fun toString(): String {
        return "\"excludes none\""
    }

    override fun hashCode(): Int {
        return 0
    }

    override fun equals(obj: Any?): Boolean {
        return this === obj
    }

    companion object {
        private val INSTANCE: ExcludeNothing = DefaultExcludeNothing()

        fun get(): ExcludeNothing {
            return INSTANCE
        }
    }
}
