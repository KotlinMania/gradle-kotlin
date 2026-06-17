/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

import com.google.common.collect.ImmutableList
import org.jspecify.annotations.NullMarked

/**
 * Data object for use with version catalog generation to encode module, version and if generated aliases should be shortened or qualified.
 */
@NullMarked
class BuildInitDependency private constructor(val module: String, val version: String?, exclusions: MutableList<DependencyExclusion>) {
    val exclusions: ImmutableList<DependencyExclusion>

    init {
        this.exclusions = ImmutableList.copyOf<DependencyExclusion>(exclusions)
    }

    fun toNotation(): String {
        return module + (if (version != null) ":" + version else "")
    }

    /**
     * Value type representing the coordinates of a dependency exclusion.
     */
    @NullMarked
    class DependencyExclusion(val group: String, val module: String)
    companion object {
        fun of(module: String, version: String): BuildInitDependency {
            return BuildInitDependency(module, version, mutableListOf<DependencyExclusion>())
        }

        fun of(group: String, name: String, version: String): BuildInitDependency {
            return BuildInitDependency(group + ":" + name, version, mutableListOf<DependencyExclusion>())
        }

        fun of(group: String, name: String, version: String, excludes: MutableList<DependencyExclusion>): BuildInitDependency {
            return BuildInitDependency(group + ":" + name, version, excludes)
        }

        fun of(module: String): BuildInitDependency {
            return BuildInitDependency(module, null, mutableListOf<DependencyExclusion>())
        }
    }
}
