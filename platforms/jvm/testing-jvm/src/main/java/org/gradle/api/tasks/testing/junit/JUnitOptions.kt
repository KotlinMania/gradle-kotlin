/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.tasks.testing.junit

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.TestFrameworkOptions
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import java.util.Arrays

/**
 * The JUnit specific test options.
 */
abstract class JUnitOptions : TestFrameworkOptions() {
    /**
     * The set of categories to run.
     */
    /**
     * The set of categories to run.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var includeCategories: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * The set of categories to exclude.
     */
    /**
     * The set of categories to exclude.
     */
    @get:ToBeReplacedByLazyProperty
    @get:Input
    var excludeCategories: MutableSet<String?> = LinkedHashSet<String?>()

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    fun copyFrom(other: JUnitOptions) {
        replace(this.includeCategories, other.includeCategories)
        replace(this.excludeCategories, other.excludeCategories)
    }

    fun includeCategories(vararg includeCategories: String?): JUnitOptions {
        this.includeCategories.addAll(Arrays.asList<String?>(*includeCategories))
        return this
    }

    fun excludeCategories(vararg excludeCategories: String?): JUnitOptions {
        this.excludeCategories.addAll(Arrays.asList<String?>(*excludeCategories))
        return this
    }


    companion object {
        private fun replace(target: MutableSet<String?>, source: MutableSet<String?>) {
            target.clear()
            target.addAll(source)
        }
    }
}
