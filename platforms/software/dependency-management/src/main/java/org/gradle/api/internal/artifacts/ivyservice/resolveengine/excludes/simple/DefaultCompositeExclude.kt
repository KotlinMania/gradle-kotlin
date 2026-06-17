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

import com.google.common.base.Objects
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet

internal abstract class DefaultCompositeExclude(private val components: PersistentSet<ExcludeSpec?>) : CompositeExclude {
    abstract fun mask(): Int

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultCompositeExclude
        return Objects.equal(components, that.components)
    }

    override fun hashCode(): Int {
        return components.hashCode()
    }

    override fun getComponents(): PersistentSet<ExcludeSpec?> {
        return components
    }

    override fun size(): Int {
        return components.size()
    }

    override fun toString(): String {
        return "{\"" + this.displayName + "\": " +
                " " + components +
                '}'
    }

    protected abstract val displayName: String
}
