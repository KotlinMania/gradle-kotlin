/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit

import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.internal.Factory
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.process.internal.worker.WorkerProcessBuilder
import java.io.File
import java.io.IOException
import java.util.stream.Collectors
import javax.inject.Inject

@UsedByScanPlugin("test-retry")
abstract class JUnitTestFramework @Inject constructor(private val filter: DefaultTestFilter, private val testTaskTemporaryDir: Factory<File?>?, private val dryRun: Provider<Boolean?>) :
    TestFramework {
    private var detector: JUnitDetector?

    init {
        this.detector = JUnitDetector(ClassFileExtractionManager(testTaskTemporaryDir))
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @UsedByScanPlugin("test-retry")
    override fun copyWithFilters(newTestFilters: TestFilter): TestFramework {
        val newTestFramework = this.objectFactory.newInstance<JUnitTestFramework>(
            JUnitTestFramework::class.java,
            newTestFilters,
            testTaskTemporaryDir,
            dryRun
        )
        newTestFramework.getOptions()!!.copyFrom(getOptions())
        return newTestFramework
    }

    override fun getProcessorFactory(): WorkerTestDefinitionProcessorFactory<*> {
        validateOptions()
        return JUnitTestDefinitionProcessorFactory(
            JUnitSpec(
                filter.toSpec(), getOptions()!!.getIncludeCategories(), getOptions()!!.getExcludeCategories(), dryRun.get()!!
            )
        )
    }

    override fun getWorkerConfigurationAction(): Action<WorkerProcessBuilder?> {
        return Action { workerProcessBuilder: WorkerProcessBuilder? ->
            workerProcessBuilder!!.sharedPackages("junit.framework")
            workerProcessBuilder.sharedPackages("junit.extensions")
            workerProcessBuilder.sharedPackages("org.junit")
        }
    }

    @Nested
    abstract override fun getOptions(): JUnitOptions?

    override fun getDetector(): JUnitDetector? {
        return detector
    }

    @Throws(IOException::class)
    override fun close() {
        // Clear expensive state from the test framework to avoid holding on to memory
        // This should probably be a part of the test task and managed there.
        detector = null
    }

    private fun validateOptions() {
        val intersection: MutableSet<String?> = Sets.newHashSet<String?>(getOptions()!!.getIncludeCategories())
        intersection.retainAll(getOptions()!!.getExcludeCategories())
        if (!intersection.isEmpty()) {
            if (intersection.size == 1) {
                LOGGER!!.warn(
                    "The category '" + intersection.iterator().next() + "' is both included and excluded.  " +
                            "This will result in the category being excluded, which may not be what was intended.  " +
                            "Please either include or exclude the category but not both."
                )
            } else {
                val allCategories = intersection.stream().sorted().map<String?> { s: String? -> "'" + s + "'" }.collect(Collectors.joining(", "))
                LOGGER!!.warn(
                    "The categories " + allCategories + " are both included and excluded.  " +
                            "This will result in the categories being excluded, which may not be what was intended. " +
                            "Please either include or exclude the categories but not both."
                )
            }
        }
    }

    override fun getDisplayName(): String {
        return "JUnit"
    }

    companion object {
        private val LOGGER = getLogger(JUnitTestFramework::class.java)
    }
}
