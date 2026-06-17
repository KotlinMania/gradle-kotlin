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
package org.gradle.api.internal.tasks.testing.testng

import org.gradle.api.Action
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.internal.Factory
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.process.internal.worker.WorkerProcessBuilder
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import javax.inject.Inject

@UsedByScanPlugin("test-retry")
abstract class TestNGTestFramework @Inject constructor(
    private val filter: DefaultTestFilter,
    private val testTaskTemporaryDir: Factory<File?>,
    private val dryRun: Provider<Boolean?>,
    html: DirectoryReport
) : TestFramework {
    private var detector: TestNGDetector?
    private val html: DirectoryReport?

    init {
        this.detector = TestNGDetector(ClassFileExtractionManager(testTaskTemporaryDir))
        this.html = html
        Companion.conventionMapOutputDirectory(getOptions()!!, html)
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @UsedByScanPlugin("test-retry")
    override fun copyWithFilters(newTestFilters: TestFilter): TestFramework {
        val newTestFramework = this.objectFactory.newInstance<TestNGTestFramework>(TestNGTestFramework::class.java, newTestFilters, testTaskTemporaryDir, dryRun, html)
        newTestFramework.getOptions()!!.copyFrom(getOptions())

        return newTestFramework
    }

    override fun getProcessorFactory(): WorkerTestDefinitionProcessorFactory<*> {
        val suiteFiles = getOptions()!!.getSuites(testTaskTemporaryDir.create())
        val spec = toSpec(getOptions()!!, filter)
        return TestNgTestDefinitionProcessorFactory(this.getOptions()!!.getOutputDirectory(), spec, suiteFiles)
    }

    private fun toSpec(options: TestNGOptions, filter: DefaultTestFilter): TestNGSpec {
        return TestNGSpec(
            filter.toSpec(),
            options.getSuiteName(), options.getTestName(), options.getParallel(), options.getThreadCount(), options.getSuiteThreadPoolSize().get(),
            options.getUseDefaultListeners(), options.getThreadPoolFactoryClass(),
            options.getIncludeGroups(), options.getExcludeGroups(), options.getListeners(),
            options.getConfigFailurePolicy(), options.getPreserveOrder(), options.getGroupByInstances(), dryRun.get()!!
        )
    }

    override fun getWorkerConfigurationAction(): Action<WorkerProcessBuilder?> {
        return Action { workerProcessBuilder: WorkerProcessBuilder? -> workerProcessBuilder!!.sharedPackages("org.testng") }
    }

    @Nested
    abstract override fun getOptions(): TestNGOptions?

    override fun getDetector(): TestNGDetector? {
        return detector
    }

    override fun getAdditionalReportEntrySkipLevels(): Int {
        // Skip `suiteName` and `testName`
        return 2
    }

    @Throws(IOException::class)
    override fun close() {
        // Clear expensive state from the test framework to avoid holding on to memory
        // This should probably be a part of the test task and managed there.
        detector = null
    }

    override fun getDisplayName(): String {
        return "TestNG"
    }

    companion object {
        private fun conventionMapOutputDirectory(options: TestNGOptions, html: DirectoryReport) {
            DslObject(options).getConventionMapping().map("outputDirectory", Callable { html.getOutputLocation().getAsFile().getOrNull() })
        }
    }
}
