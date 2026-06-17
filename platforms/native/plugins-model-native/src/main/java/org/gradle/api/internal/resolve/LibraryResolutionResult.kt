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
package org.gradle.api.internal.resolve

import com.google.common.base.Function
import com.google.common.base.Joiner
import com.google.common.base.Predicate
import com.google.common.collect.Lists
import com.google.common.collect.Ordering
import org.gradle.api.artifacts.component.LibraryComponentSelector
import org.gradle.platform.base.Binary
import org.gradle.platform.base.VariantComponent

/**
 * Intermediate data structure used to store the result of a resolution and help at building an understandable error message in case resolution fails.
 */
class LibraryResolutionResult private constructor(private val binaryType: Class<out Binary?>) {
    private val libsMatchingRequirements: MutableMap<String?, VariantComponent?>
    private val libsNotMatchingRequirements: MutableMap<String?, VariantComponent?>

    var isProjectNotFound: Boolean = false
        private set

    var selectedLibrary: VariantComponent? = null
        private set
    var nonMatchingLibrary: VariantComponent? = null
        private set

    init {
        this.libsMatchingRequirements = HashMap<String?, VariantComponent?>()
        this.libsNotMatchingRequirements = HashMap<String?, VariantComponent?>()
    }

    private val singleMatchingLibrary: VariantComponent?
        get() {
            if (libsMatchingRequirements.size == 1) {
                return libsMatchingRequirements.values.iterator().next()
            }
            return null
        }

    private fun resolve(libraryName: String?) {
        var libraryName = libraryName
        if (libraryName == null) {
            val singleMatchingLibrary = this.singleMatchingLibrary
            if (singleMatchingLibrary == null) {
                return
            }
            libraryName = singleMatchingLibrary.getName()
        }

        selectedLibrary = libsMatchingRequirements.get(libraryName)
        nonMatchingLibrary = libsNotMatchingRequirements.get(libraryName)
    }

    fun hasLibraries(): Boolean {
        return !libsMatchingRequirements.isEmpty() || !libsNotMatchingRequirements.isEmpty()
    }

    val candidateLibraries: MutableList<String?>
        get() = Lists.newArrayList<String?>(libsMatchingRequirements.keys)

    fun toResolutionErrorMessage(
        selector: LibraryComponentSelector
    ): String {
        val candidateLibraries: MutableList<String?> = formatLibraryNames(this.candidateLibraries)
        val projectPath = selector.getProjectPath()
        val libraryName = selector.getLibraryName()

        val sb = StringBuilder("Project '").append(projectPath).append("'")
        if (libraryName == null || !hasLibraries()) {
            if (this.isProjectNotFound) {
                sb.append(" not found.")
            } else if (!hasLibraries()) {
                sb.append(" doesn't define any library.")
            } else {
                sb.append(" contains more than one library. Please select one of ")
                Joiner.on(", ").appendTo(sb, candidateLibraries)
            }
        } else {
            val notMatchingRequirements = this.nonMatchingLibrary
            if (notMatchingRequirements != null) {
                sb.append(" contains a library named '").append(libraryName)
                    .append("' but it doesn't have any binary of type ")
                    .append(binaryType.getSimpleName())
            } else {
                sb.append(" does not contain library '").append(libraryName).append("'. Did you want to use ")
                if (candidateLibraries.size == 1) {
                    sb.append(candidateLibraries.get(0))
                } else {
                    sb.append("one of ")
                    Joiner.on(", ").appendTo(sb, candidateLibraries)
                }
                sb.append("?")
            }
        }
        return sb.toString()
    }

    companion object {
        val QUOTE_TRANSFORMER: Function<String?, String?> = object : Function<String?, String?> {
            override fun apply(input: String): String {
                return "'" + input + "'"
            }
        }

        fun of(binaryType: Class<out Binary?>, libraries: MutableCollection<out VariantComponent>, libraryName: String?, libraryFilter: Predicate<in VariantComponent?>): LibraryResolutionResult {
            val result = LibraryResolutionResult(binaryType)
            for (librarySpec in libraries) {
                if (libraryFilter.apply(librarySpec)) {
                    result.libsMatchingRequirements.put(librarySpec.getName(), librarySpec)
                } else {
                    result.libsNotMatchingRequirements.put(librarySpec.getName(), librarySpec)
                }
            }
            result.resolve(libraryName)
            return result
        }

        fun projectNotFound(binaryType: Class<out Binary?>): LibraryResolutionResult {
            val projectNotFoundResult = LibraryResolutionResult(binaryType)
            projectNotFoundResult.isProjectNotFound = true
            return projectNotFoundResult
        }

        fun emptyResolutionResult(binaryType: Class<out Binary?>): LibraryResolutionResult {
            return LibraryResolutionResult(binaryType)
        }

        private fun formatLibraryNames(libs: MutableList<String?>): MutableList<String?> {
            val list = Lists.transform<String?, String?>(libs, QUOTE_TRANSFORMER)
            return Ordering.natural<Comparable<*>?>().sortedCopy<String?>(list)
        }
    }
}
