/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

@NullMarked
class DefaultHeaderDependenciesCollector(private val directoryFileTreeFactory: DirectoryFileTreeFactory) : HeaderDependenciesCollector {
    private val logger: Logger = LoggerFactory.getLogger(DefaultHeaderDependenciesCollector::class.java)

    override fun collectExistingHeaderDependencies(taskPath: String, includeRoots: MutableList<File>, incrementalCompilation: IncrementalCompilation): ImmutableSortedSet<File> {
        val headerDependencies: MutableSet<File> = HashSet<File>()
        if (incrementalCompilation.isUnresolvedHeaders()) {
            logger.info(
                "After parsing the source files, Gradle cannot calculate the exact set of include files for '{}'. Every file in the include search path will be considered a header dependency.",
                taskPath
            )
            addIncludeRoots(taskPath, includeRoots, headerDependencies)
        } else {
            logger.info("Found all include files for '{}'", taskPath)
            headerDependencies.addAll(incrementalCompilation.getExistingHeaders())
        }
        return ImmutableSortedSet.copyOf<File>(headerDependencies)
    }

    private fun addIncludeRoots(taskPath: String, includeRoots: MutableList<File>, headerDependencies: MutableSet<File>) {
        for (includeRoot in includeRoots) {
            logger.info("adding files in {} to header dependencies for {}", includeRoot, taskPath)
            directoryFileTreeFactory.create(includeRoot).visit(object : EmptyFileVisitor() {
                override fun visitFile(fileDetails: FileVisitDetails) {
                    headerDependencies.add(fileDetails.getFile())
                }
            })
        }
    }
}
