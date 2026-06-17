/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.enterprise.test.impl

import com.google.common.collect.ImmutableSet
import org.gradle.internal.enterprise.test.TestTaskFilters

internal class DefaultTestTaskFilters(
    includePatterns: MutableSet<String>,
    commandLineIncludePatterns: MutableSet<String>,
    excludePatterns: MutableSet<String>,
    includeTags: MutableSet<String>,
    excludeTags: MutableSet<String>,
    includeEngines: MutableSet<String>,
    excludeEngines: MutableSet<String>
) : TestTaskFilters {
    private val includePatterns: MutableSet<String>
    private val commandLineIncludePatterns: MutableSet<String>
    private val excludePatterns: MutableSet<String>
    private val includeTags: MutableSet<String>
    private val excludeTags: MutableSet<String>
    private val includeEngines: MutableSet<String>
    private val excludeEngines: MutableSet<String>

    init {
        this.includePatterns = ImmutableSet.copyOf<String>(includePatterns)
        this.commandLineIncludePatterns = ImmutableSet.copyOf<String>(commandLineIncludePatterns)
        this.excludePatterns = ImmutableSet.copyOf<String>(excludePatterns)
        this.includeTags = ImmutableSet.copyOf<String>(includeTags)
        this.excludeTags = ImmutableSet.copyOf<String>(excludeTags)
        this.includeEngines = ImmutableSet.copyOf<String>(includeEngines)
        this.excludeEngines = ImmutableSet.copyOf<String>(excludeEngines)
    }

    override fun getIncludePatterns(): MutableSet<String> {
        return includePatterns
    }

    override fun getCommandLineIncludePatterns(): MutableSet<String> {
        return commandLineIncludePatterns
    }

    override fun getExcludePatterns(): MutableSet<String> {
        return excludePatterns
    }

    override fun getIncludeTags(): MutableSet<String> {
        return includeTags
    }

    override fun getExcludeTags(): MutableSet<String> {
        return excludeTags
    }

    override fun getIncludeEngines(): MutableSet<String> {
        return includeEngines
    }

    override fun getExcludeEngines(): MutableSet<String> {
        return excludeEngines
    }
}
