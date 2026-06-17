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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

class ModuleComponentAtRepositoryKey internal constructor(val repositoryId: String, val componentId: ModuleComponentIdentifier) {
    private val hashCode: Int

    init {
        this.hashCode = 31 * repositoryId.hashCode() + componentId.hashCode()
    }

    override fun toString(): String {
        return repositoryId + "," + componentId
    }

    override fun equals(o: Any?): Boolean {
        if (o !is ModuleComponentAtRepositoryKey) {
            return false
        }
        val other = o
        return hashCode == other.hashCode && repositoryId == other.repositoryId && componentId == other.componentId
    }

    override fun hashCode(): Int {
        return hashCode
    }
}
