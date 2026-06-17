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
package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec
import org.gradle.api.internal.tasks.testing.TestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.TestExecuter
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.WorkerTestDefinitionProcessorFactory
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.processors.PatternMatchTestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.processors.RestartEveryNTestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.processors.RunPreviousFailedFirstTestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.processors.TestMainAction
import org.gradle.api.internal.tasks.testing.results.TestRetryShieldingTestResultProcessor
import org.gradle.api.internal.tasks.testing.worker.ForkingTestDefinitionProcessor
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.Factory
import org.gradle.internal.actor.ActorFactory
import org.gradle.internal.time.Clock
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import java.io.File

/**
 * The default test class scanner factory.
 */
class DefaultTestExecuter(
    private val workerFactory: WorkerProcessFactory?, private val actorFactory: ActorFactory?, moduleRegistry: ModuleRegistry?,
    private val workerLeaseService: WorkerLeaseService?, private val maxWorkerCount: Int,
    private val clock: Clock?, private val testFilter: DefaultTestFilter
) : TestExecuter<JvmTestExecutionSpec?> {
    private val testClasspathFactory: ForkedTestClasspathFactory
    private var processor: TestDefinitionProcessor<TestDefinition?>? = null

    init {
        this.testClasspathFactory = ForkedTestClasspathFactory(moduleRegistry!!)
    }

    override fun execute(testExecutionSpec: JvmTestExecutionSpec, testResultProcessor: TestResultProcessor?) {
        var testResultProcessor = testResultProcessor
        val testFramework = testExecutionSpec.getTestFramework()
        // Cast away from ? so we don't need to propagate the wildcard everywhere
        // This is safe because the frameworks that don't accept all TestDefinitions will have the dir selection filtered out earlier
        // If a TestFramework begins to reject ClassTestDefinitions, this needs rethinking.
        val testInstanceFactory = uncheckedNonnullCast<WorkerTestDefinitionProcessorFactory<TestDefinition?>?>(
            testFramework.getProcessorFactory()
        )

        val classpath = testClasspathFactory.create(
            testExecutionSpec.getClasspath(),
            testExecutionSpec.getModulePath()
        )

        val forkingProcessorFactory: Factory<TestDefinitionProcessor<TestDefinition?>?> = org.gradle.internal.Factory {
            ForkingTestDefinitionProcessor<TestDefinition?>(
                workerLeaseService,
                workerFactory,
                testInstanceFactory,
                testExecutionSpec.getJavaForkOptions(),
                classpath,
                testFramework.getWorkerConfigurationAction()
            )
        }
        val reforkingProcessorFactory: Factory<TestDefinitionProcessor<TestDefinition?>?> =
            org.gradle.internal.Factory { RestartEveryNTestDefinitionProcessor<TestDefinition?>(forkingProcessorFactory, testExecutionSpec.getForkEvery()) }
        processor =
            PatternMatchTestDefinitionProcessor<TestDefinition?>(
                testFilter,
                RunPreviousFailedFirstTestDefinitionProcessor<TestDefinition?>(
                    testExecutionSpec.getPreviousFailedTestClasses(), mutableSetOf<File?>(),
                    MaxNParallelTestDefinitionProcessor<TestDefinition?>(getMaxParallelForks(testExecutionSpec), reforkingProcessorFactory, actorFactory)
                )
            )

        val testClassFiles = testExecutionSpec.getCandidateClassFiles()
        val testDefinitionDirs = testExecutionSpec.getCandidateTestDefinitionDirs()

        // When scanForTestClasses is false, the contract is that classes matching the include/exclude patterns
        // are executed as test classes without inspecting their bytecode. Suppress the framework detector so
        // that DefaultTestScanner falls back to a filename-only scan.
        val frameworkDetector = if (testExecutionSpec.isScanForTestClasses()) testFramework.getDetector() else null
        if (frameworkDetector != null) {
            frameworkDetector.setTestClasses(ArrayList<File?>(testExecutionSpec.getTestClassesDirs().getFiles()))
            frameworkDetector.setTestClasspath(classpath.getApplicationClasspath())
        }

        /*
         * We're possibly running non-class-based tests and there is a filter present.
         *
         * @see org.gradle.api.internal.tasks.testing.filter.FileTestSelectionMatcher
         */
        if (!testDefinitionDirs.isEmpty() && testFilter.hasPatterns()) {
            incubatingFeatureUsed("Filtering non-class-based tests")
        }

        val detector: TestDetector = DefaultTestScanner(testClassFiles!!, testDefinitionDirs, frameworkDetector!!, processor)

        // What is this?
        // In some versions of the Gradle retry plugin, it would retry any test that had any kind of failure associated with it.
        // We attempt to capture assumption violations as failures for skipped tests.
        //
        // This would cause any test that had been skipped to be executed multiple times. This could sometimes cause real failures.
        // To work around this, we shield the test retry result processor from seeing test assumption failures.
        if (testResultProcessor != null) {
            // KMP calls this code with a delegating test result processor that does not return sensible Class objects
            val canonicalName = testResultProcessor.javaClass.getCanonicalName()
            if (canonicalName != null && canonicalName.endsWith("org.gradle.testretry.internal.executer.RetryTestResultProcessor")) {
                testResultProcessor = TestRetryShieldingTestResultProcessor(testResultProcessor)
            }
        }
        TestMainAction(detector, processor, testResultProcessor, workerLeaseService, clock, testExecutionSpec.getPath(), "Gradle Test Run " + testExecutionSpec.getIdentityPath()).run()
    }

    override fun stopNow() {
        if (processor != null) {
            processor!!.stopNow()
        }
    }

    private fun getMaxParallelForks(testExecutionSpec: JvmTestExecutionSpec): Int {
        var maxParallelForks = testExecutionSpec.getMaxParallelForks()
        if (maxParallelForks > maxWorkerCount) {
            LOGGER!!.info("{}.maxParallelForks ({}) is larger than max-workers ({}), forcing it to {}", testExecutionSpec.getPath(), maxParallelForks, maxWorkerCount, maxWorkerCount)
            maxParallelForks = maxWorkerCount
        }
        return maxParallelForks
    }

    companion object {
        private val LOGGER = getLogger(DefaultTestExecuter::class.java)
    }
}
