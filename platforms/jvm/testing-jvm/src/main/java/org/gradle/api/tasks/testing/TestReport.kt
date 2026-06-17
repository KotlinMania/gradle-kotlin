/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.tasks.testing

import org.gradle.api.DefaultTask
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path
import javax.inject.Inject

/**
 * Generates an HTML test report from the results of one or more [Test] tasks.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class TestReport : DefaultTask() {
    @get:Inject
    protected abstract val objectFactory: ObjectFactory?

    @get:Deprecated("")
    @get:Inject
    @get:Suppress("unused")
    protected abstract val buildOperationRunner: BuildOperationRunner?

    @get:Deprecated("")
    @get:Inject
    @get:Suppress("unused")
    protected abstract val buildOperationExecutor: BuildOperationExecutor?

    @get:Internal
    @get:Deprecated("")
    @get:Suppress("unused")
    protected val metadataRendererRegistry: MetadataRendererRegistry
        // Method kept for binary compatibility remove in Gradle 10
        get() = MetadataRendererRegistry()

    @get:ReplacesEagerProperty(
        replacedAccessors = [ReplacedAccessor(
            value = ReplacedAccessor.AccessorType.GETTER,
            name = "getDestinationDir"
        ), ReplacedAccessor(
            value = ReplacedAccessor.AccessorType.SETTER,
            name = "setDestinationDir"
        )],
        deprecation = ReplacedDeprecation(removedIn = ReplacedDeprecation.RemovedIn.GRADLE9)
    )
    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty?

    @get:PathSensitive(PathSensitivity.NONE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val testResults: ConfigurableFileCollection?

    @TaskAction
    fun generateReport() {
        try {
            val resultDirsAsPaths: MutableList<Path?> = ArrayList<Path?>(this.testResults.getFiles().size)
            for (resultDir in this.testResults.getFiles()) {
                if (!resultDir.exists()) {
                    continue
                }
                resultDirsAsPaths.add(resultDir.toPath())
            }

            val reportsDir = this.destinationDirectory.get().getAsFile().toPath()
            this.objectFactory.newInstance<GenericHtmlTestReportGenerator?>(GenericHtmlTestReportGenerator::class.java, reportsDir).generate(resultDirsAsPaths)
        } catch (e: Exception) {
            throw RuntimeException("Could not write test report for results in " + this.testResults.getFiles(), e)
        }
    }
}
