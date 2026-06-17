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
package org.gradle.nativeplatform.test.xctest.tasks

import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestExecuter
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestSelection
import org.gradle.nativeplatform.test.xctest.internal.execution.XCTestTestExecutionSpec
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Executes XCTest tests. Test are always run in a single execution.
 *
 * @since 4.5
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class XCTest : AbstractTestTask() {
    override fun createTestExecutionSpec(): XCTestTestExecutionSpec {
        val testFilter = getFilter() as DefaultTestFilter

        return XCTestTestExecutionSpec(
            this.workingDirectory.getAsFile().get(), this.runScriptFile.getAsFile().get(), getPath(),
            XCTestSelection(testFilter.getIncludePatterns(), testFilter.getCommandLineIncludePatterns())
        )
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testInstallDirectory: DirectoryProperty?

    @get:Internal("Covered by getRunScript")
    abstract val runScriptFile: RegularFileProperty?

    @get:Internal
    abstract val workingDirectory: DirectoryProperty?

    override fun createTestExecuter(): TestExecuter<XCTestTestExecutionSpec?> {
        return getObjectFactory().newInstance<XCTestExecuter>(XCTestExecuter::class.java)
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:Optional
    @get:SkipWhenEmpty
    protected val runScript: File?
        /**
         * Workaround for when the task is given an input file that doesn't exist
         */
        get() {
            val runScript = this.runScriptFile.get()
            val runScriptFile = runScript.getAsFile()
            if (!runScriptFile.exists()) {
                return null
            }
            return runScriptFile
        }

    /**
     * {@inheritDoc}
     */
    override fun setTestNameIncludePatterns(testNamePattern: MutableList<String?>?): XCTest {
        super.setTestNameIncludePatterns(testNamePattern)
        return this
    }
}
