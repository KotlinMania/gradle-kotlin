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
package org.gradle.internal.component.external.descriptor

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.IvyArtifactName

class DefaultExclude : Exclude {
    val moduleId: ModuleIdentifier
    val artifact: IvyArtifactName?
    val configurations: MutableSet<String?>
    val matcher: String?

    constructor(id: ModuleIdentifier, artifact: IvyArtifactName?, configurations: Array<String?>, patternMatcher: String?) {
        this.moduleId = id
        this.artifact = artifact
        this.matcher = patternMatcher
        this.configurations = ImmutableSet.copyOf<String?>(configurations)
    }

    constructor(id: ModuleIdentifier, configurations: Array<String?>, patternMatcher: String?) {
        this.moduleId = id
        this.artifact = null
        this.matcher = patternMatcher
        this.configurations = ImmutableSet.copyOf<String?>(configurations)
    }

    constructor(id: ModuleIdentifier) {
        this.moduleId = id
        this.artifact = null
        this.matcher = null
        this.configurations = ImmutableSet.of<String?>()
    }

    override fun toString(): String {
        return "{exclude moduleId: " + moduleId + ", artifact: " + artifact + "}"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultExclude

        if (moduleId != that.moduleId) {
            return false
        }
        if (artifact != that.artifact) {
            return false
        }
        if (configurations != that.configurations) {
            return false
        }
        return this.matcher == that.matcher
    }

    override fun hashCode(): Int {
        var result = moduleId.hashCode()
        result = 31 * result + (if (artifact != null) artifact.hashCode() else 0)
        result = 31 * result + configurations.hashCode()
        result = 31 * result + (if (this.matcher != null) matcher.hashCode() else 0)
        return result
    }
}
