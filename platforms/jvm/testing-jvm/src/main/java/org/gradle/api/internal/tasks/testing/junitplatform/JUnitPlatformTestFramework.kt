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
package org.gradle.api.internal.tasks.testing.junitplatform

import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.testing.TestFilter
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.internal.scan.UsedByScanPlugin
import org.gradle.process.internal.worker.WorkerProcessBuilder
import java.io.IOException
import java.util.stream.Collectors
import javax.inject.Inject

@UsedByScanPlugin("test-retry")
abstract class JUnitPlatformTestFramework @Inject constructor(private val filter: DefaultTestFilter, private val dryRun: Provider<Boolean?>, private val projectLayout: ProjectLayout?) :
    TestFramework {
    @UsedByScanPlugin("test-retry")
    override fun copyWithFilters(newTestFilters: TestFilter): TestFramework {
        val newTestFramework = this.objectFactory.newInstance<JUnitPlatformTestFramework>(
            JUnitPlatformTestFramework::class.java,
            newTestFilters,
            dryRun,
            projectLayout
        )

        newTestFramework.getOptions()!!.copyFrom(getOptions())
        return newTestFramework
    }

    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    override fun getProcessorFactory(): WorkerTestDefinitionProcessorFactory<*> {
        validateOptions()
        return JUnitPlatformTestDefinitionProcessorFactory(
            JUnitPlatformSpec(
                filter.toSpec(), getOptions()!!.getIncludeEngines(), getOptions()!!.getExcludeEngines(),
                getOptions()!!.getIncludeTags(), getOptions()!!.getExcludeTags(), dryRun.get()!!
            )
        )
    }

    override fun getWorkerConfigurationAction(): Action<WorkerProcessBuilder?> {
        return Action { workerProcessBuilder: WorkerProcessBuilder? -> workerProcessBuilder!!.sharedPackages("org.junit") }
    }

    @Nested
    abstract override fun getOptions(): JUnitPlatformOptions?

    override fun getDetector(): TestFrameworkDetector? {
        return null
    }

    @Throws(IOException::class)
    override fun close() {
        // this test framework doesn't hold any state
    }

    private fun validateOptions() {
        val intersection: MutableSet<String?> = Sets.newHashSet<String?>(getOptions()!!.getIncludeTags())
        intersection.retainAll(getOptions()!!.getExcludeTags())
        if (!intersection.isEmpty()) {
            if (intersection.size == 1) {
                LOGGER!!.warn(
                    "The tag '" + intersection.iterator().next() + "' is both included and excluded.  " +
                            "This will result in the tag being excluded, which may not be what was intended.  " +
                            "Please either include or exclude the tag but not both."
                )
            } else {
                val allTags = intersection.stream().sorted().map<String?> { s: String? -> "'" + s + "'" }.collect(Collectors.joining(", "))
                LOGGER!!.warn(
                    "The tags " + allTags + " are both included and excluded.  " +
                            "This will result in the tags being excluded, which may not be what was intended.  " +
                            "Please either include or exclude the tags but not both."
                )
            }
        }
    }

    override fun supportsNonClassBasedTesting(): Boolean {
        return true
    }

    override fun getDisplayName(): String {
        return "JUnit Platform"
    }

    companion object {
        private val LOGGER = getLogger(JUnitPlatformTestFramework::class.java)
    }
}
