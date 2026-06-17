/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.tooling.internal.provider.continuous

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.execution.plan.SingleFileTreeElementMatcher
import org.gradle.execution.plan.ValuedVfsHierarchy
import org.gradle.internal.collect.PersistentList
import org.gradle.internal.file.Stat
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.VfsRelativePath
import java.io.File
import java.util.function.Supplier
import kotlin.concurrent.Volatile

/**
 * Allows recording and querying the input locations of a build.
 */
class BuildInputHierarchy(caseSensitivity: CaseSensitivity, stat: Stat) {
    @Volatile
    private var root: ValuedVfsHierarchy<InputDeclaration>
    private val matcher: SingleFileTreeElementMatcher

    /**
     * Returns whether the given location is an input to the build.
     */
    fun isInput(location: String): Boolean {
        val visitor = InputDeclarationVisitor()
        root.visitValues(location, visitor)
        return visitor.isInput
    }

    val isEmpty: Boolean
        get() = root.isEmpty()

    /**
     * Records that some locations are an input to the build.
     */
    @Synchronized
    fun recordInputs(inputLocations: Iterable<String>) {
        for (location in inputLocations) {
            val relativePath = VfsRelativePath.of(location)
            root = root.recordValue(relativePath, ALL_CHILDREN_ARE_INPUTS)
        }
    }

    /**
     * Records that a fileTreeRoot with a filter is an input to the build.
     *
     * Only children of the fileTreeRoot that match the filter are considered inputs.
     */
    @Synchronized
    fun recordFilteredInput(fileTreeRoot: String, filter: Spec<FileTreeElement>) {
        val relativePath = VfsRelativePath.of(fileTreeRoot)
        root = root.recordValue(relativePath, BuildInputHierarchy.FilteredInputDeclaration(filter))
    }

    private class InputDeclarationVisitor : ValuedVfsHierarchy.ValueVisitor<InputDeclaration> {
        var isInput: Boolean = false
            private set

        fun foundInput() {
            this.isInput = true
        }

        override fun visitExact(value: InputDeclaration) {
            foundInput()
        }

        override fun visitAncestor(ancestor: InputDeclaration, pathToVisitedLocation: VfsRelativePath) {
            if (ancestor.contains(pathToVisitedLocation)) {
                foundInput()
            }
        }

        override fun visitChildren(values: PersistentList<InputDeclaration>, relativePathSupplier: Supplier<String>) {
            // A parent directory to the input is not an input.
            // As long as nothing within the input location changes we don't need to trigger a build.
            // If we would consider the parents as inputs, then the creation of parent directories for
            // an output file produced by the current build would directly trigger, though actually
            // everything is up-to-date.
        }
    }

    private interface InputDeclaration {
        fun contains(childPath: VfsRelativePath): Boolean
    }

    init {
        this.root = ValuedVfsHierarchy.emptyHierarchy<InputDeclaration>(caseSensitivity)
        this.matcher = SingleFileTreeElementMatcher(stat)
    }

    private inner class FilteredInputDeclaration(private val spec: Spec<FileTreeElement>) : InputDeclaration {
        override fun contains(childPath: VfsRelativePath): Boolean {
            return matcher.elementWithRelativePathMatches(spec, File(childPath.getAbsolutePath()), childPath.getAsString())
        }
    }

    companion object {
        private val ALL_CHILDREN_ARE_INPUTS = BuildInputHierarchy.InputDeclaration { childPath: VfsRelativePath -> true }
    }
}
