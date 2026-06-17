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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.internal.component.model.PersistentModuleSource
import org.gradle.internal.hash.HashCode

class ModuleDescriptorHashModuleSource(@JvmField val descriptorHash: HashCode, val isChangingModule: Boolean) : PersistentModuleSource {
    override fun toString(): String {
        return "{descriptor: " + descriptorHash + ", changing: " + this.isChangingModule + "}"
    }

    companion object {
        val codecId: Int = 2
            get() = Companion.field
    }
}
