/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.tasks.testing.junitplatform

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.TestFrameworkOptions
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.util.Arrays

/**
 * The JUnit platform specific test options.
 *
 * @see [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide)
 *
 * @since 4.6
 */
abstract class JUnitPlatformOptions : TestFrameworkOptions() {
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var includeEngines: MutableSet<String?> = LinkedHashSet<String?>()

    @get:ToBeReplacedByLazyProperty
    @get:Input
    var excludeEngines: MutableSet<String?> = LinkedHashSet<String?>()

    @get:ToBeReplacedByLazyProperty
    @get:Input
    var includeTags: MutableSet<String?> = LinkedHashSet<String?>()

    @get:ToBeReplacedByLazyProperty
    @get:Input
    var excludeTags: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    fun copyFrom(other: JUnitPlatformOptions) {
        replace(this.includeEngines, other.includeEngines)
        replace(this.excludeEngines, other.excludeEngines)
        replace(this.includeTags, other.includeTags)
        replace(this.excludeTags, other.excludeTags)
    }

    /**
     * The set of engines to run with.
     *
     * @see [Test Engine](https://junit.org/junit5/docs/current/user-guide/.launcher-api-engines-custom)
     */
    fun includeEngines(vararg includeEngines: String?): JUnitPlatformOptions {
        this.includeEngines.addAll(Arrays.asList<String?>(*includeEngines))
        return this
    }

    /**
     * The set of tags to run with.
     *
     * @see [Tagging and Filtering](https://junit.org/junit5/docs/current/user-guide/.writing-tests-tagging-and-filtering)
     */
    fun includeTags(vararg includeTags: String?): JUnitPlatformOptions {
        this.includeTags.addAll(Arrays.asList<String?>(*includeTags))
        return this
    }

    /**
     * The set of engines to exclude.
     *
     * @see [Test Engine](https://junit.org/junit5/docs/current/user-guide/.launcher-api-engines-custom)
     */
    fun excludeEngines(vararg excludeEngines: String?): JUnitPlatformOptions {
        this.excludeEngines.addAll(Arrays.asList<String?>(*excludeEngines))
        return this
    }

    /**
     * The set of tags to exclude.
     *
     * @see [Tagging and Filtering](https://junit.org/junit5/docs/current/user-guide/.writing-tests-tagging-and-filtering)
     */
    fun excludeTags(vararg excludeTags: String?): JUnitPlatformOptions {
        this.excludeTags.addAll(Arrays.asList<String?>(*excludeTags))
        return this
    }

    companion object {
        private fun replace(target: MutableSet<String?>, source: MutableSet<String?>) {
            target.clear()
            target.addAll(source)
        }
    }
}
