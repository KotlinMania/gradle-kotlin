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
package org.gradle.api.internal.tasks.testing

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.process.JavaForkOptions
import org.gradle.util.Path
import java.io.File

@UsedByScanPlugin("test-distribution, test-retry")
class JvmTestExecutionSpec(
    val testFramework: TestFramework?, val classpath: Iterable<out File?>?, val modulePath: Iterable<out File?>?,
    val candidateClassFiles: FileTree?, val isScanForTestClasses: Boolean,
    val candidateTestDefinitionDirs: MutableSet<File?>?,
    @get:UsedByScanPlugin("test-retry") val testClassesDirs: FileCollection?, val path: String?, val identityPath: Path?, val forkEvery: Long, @get:UsedByScanPlugin(
        "test-distribution"
    ) val javaForkOptions: JavaForkOptions?, val maxParallelForks: Int, val previousFailedTestClasses: MutableSet<String?>?, private val testIsModule: Boolean
) : TestExecutionSpec {
    @UsedByScanPlugin("test-distribution, pts")
    constructor(
        testFramework: TestFramework?,
        classpath: Iterable<out File?>?,
        modulePath: Iterable<out File?>?,
        candidateClassFiles: FileTree?,
        scanForTestClasses: Boolean,
        testClassesDirs: FileCollection?,
        path: String?,
        identityPath: Path?,
        forkEvery: Long,
        javaForkOptions: JavaForkOptions?,
        maxParallelForks: Int,
        previousFailedTestClasses: MutableSet<String?>?,
        testIsModule: Boolean
    ) : this(
        testFramework,
        classpath,
        modulePath,
        candidateClassFiles,
        scanForTestClasses,
        mutableSetOf<File?>(),
        testClassesDirs,
        path,
        identityPath,
        forkEvery,
        javaForkOptions,
        maxParallelForks,
        previousFailedTestClasses,
        testIsModule
    )

    @Suppress("unused")
    @UsedByScanPlugin("test-retry")
    fun copyWithTestFramework(testFramework: TestFramework?): JvmTestExecutionSpec {
        return JvmTestExecutionSpec(
            testFramework, this.classpath, this.modulePath,
            this.candidateClassFiles, this.isScanForTestClasses, this.candidateTestDefinitionDirs,
            this.testClassesDirs, this.path, this.identityPath, this.forkEvery,
            this.javaForkOptions, this.maxParallelForks, this.previousFailedTestClasses, this.testIsModule
        )
    }
}
