/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.properties.InputBehavior
import java.io.File
import java.util.EnumSet
import java.util.Objects
import java.util.function.Consumer

class AccumulateBuildInputsListener(private val buildInputHierarchy: BuildInputHierarchy) : WorkInputListener {
    override fun onExecute(work: UnitOfWork, relevantBehaviors: EnumSet<InputBehavior>) {
        val taskInputs: MutableSet<String> = LinkedHashSet<String>()
        val filteredFileTreeTaskInputs: MutableSet<FilteredTree> = LinkedHashSet<FilteredTree>()
        work.visitMutableInputs(object : InputVisitor {
            override fun visitInputFileProperty(propertyName: String, behavior: InputBehavior, value: InputVisitor.InputFileValueSupplier) {
                if (relevantBehaviors.contains(behavior)) {
                    (value.getFiles() as FileCollectionInternal).visitStructure(object : FileCollectionStructureVisitor {
                        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
                            contents.forEach(Consumer { location: File? -> taskInputs.add(location!!.getAbsolutePath()) })
                        }

                        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
                            if (patterns.isEmpty()) {
                                taskInputs.add(root.getAbsolutePath())
                            } else {
                                filteredFileTreeTaskInputs.add(FilteredTree(root.getAbsolutePath(), patterns))
                            }
                        }

                        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
                            taskInputs.add(file.getAbsolutePath())
                        }
                    })
                }
            }
        })
        buildInputHierarchy.recordInputs(taskInputs)
        filteredFileTreeTaskInputs.forEach(Consumer { fileTree: FilteredTree? ->
            buildInputHierarchy.recordFilteredInput(
                fileTree!!.root, fileTree.patterns.getAsSpec()
            )
        })
    }

    private class FilteredTree(val root: String, val patterns: PatternSet) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as FilteredTree
            return root == that.root && patterns == that.patterns
        }

        override fun hashCode(): Int {
            return Objects.hash(root, patterns)
        }
    }
}
