/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.enterprise.test.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.EmptyFileVisitor
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.internal.TaskOutputsEnterpriseInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.TaskPropertyUtils
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestFrameworkOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.internal.enterprise.test.CandidateClassFile
import org.gradle.internal.enterprise.test.InputFileProperty
import org.gradle.internal.enterprise.test.OutputFileProperty
import org.gradle.internal.enterprise.test.TestTaskFilters
import org.gradle.internal.enterprise.test.TestTaskForkOptions
import org.gradle.internal.enterprise.test.TestTaskProperties
import org.gradle.internal.enterprise.test.TestTaskPropertiesService
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.jvm.JavaModuleDetector
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.OutputFilePropertyType
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import org.gradle.process.internal.JavaForkOptionsFactory
import java.util.function.Function
import javax.inject.Inject

class DefaultTestTaskPropertiesService @Inject constructor(
    private val propertyWalker: PropertyWalker,
    private val fileCollectionFactory: FileCollectionFactory,
    private val forkOptionsFactory: JavaForkOptionsFactory,
    private val jvmVersionDetector: JvmVersionDetector,
    javaModuleDetector: JavaModuleDetector
) : TestTaskPropertiesService {
    private val javaModuleDetector: JavaModuleDetector

    init {
        this.javaModuleDetector = javaModuleDetector
    }

    override fun collectProperties(task: Test): TestTaskProperties {
        val inputFileProperties = ImmutableList.builder<InputFileProperty>()
        val outputFileProperties = ImmutableList.builder<OutputFileProperty>()
        TaskPropertyUtils.visitProperties(propertyWalker, task, object : PropertyVisitor {
            override fun visitInputFileProperty(
                propertyName: String,
                optional: Boolean,
                behavior: InputBehavior,
                directorySensitivity: DirectorySensitivity,
                lineEndingSensitivity: LineEndingSensitivity,
                fileNormalizer: FileNormalizer?,
                value: PropertyValue,
                filePropertyType: InputFilePropertyType
            ) {
                val files = resolveLeniently(value)
                inputFileProperties.add(DefaultInputFileProperty(propertyName, files))
            }

            override fun visitOutputFileProperty(
                propertyName: String,
                optional: Boolean,
                value: PropertyValue,
                filePropertyType: OutputFilePropertyType
            ) {
                val files = resolveLeniently(value)
                val type = if (filePropertyType.getOutputType() == TreeType.DIRECTORY)
                    OutputFileProperty.Type.DIRECTORY
                else
                    OutputFileProperty.Type.FILE
                outputFileProperties.add(DefaultOutputFileProperty(propertyName, files, type))
            }
        })
        return DefaultTestTaskProperties(
            task.getOptions() is JUnitPlatformOptions,
            task.getForkEvery(),
            task.dryRun.get(),
            collectFilters(task),
            collectForkOptions(task),
            collectCandidateClassFiles(task),
            inputFileProperties.build(),
            outputFileProperties.build()
        )
    }

    override fun doNotStoreInCache(task: Test) {
        (task.getOutputs() as TaskOutputsEnterpriseInternal).doNotStoreInCache()
    }

    private fun collectCandidateClassFiles(task: Test): ImmutableList<CandidateClassFile> {
        val builder = ImmutableList.builder<CandidateClassFile>()
        task.getCandidateClassFiles().visit(object : EmptyFileVisitor() {
            override fun visitFile(fileDetails: FileVisitDetails) {
                builder.add(DefaultCandidateClassFile(fileDetails.getFile(), fileDetails.getPath()))
            }
        })
        return builder.build()
    }

    private fun resolveLeniently(value: PropertyValue): FileCollection {
        val sources = value.call()
        return if (sources == null)
            FileCollectionFactory.empty()
        else
            fileCollectionFactory.resolvingLeniently(sources)
    }

    private fun collectFilters(task: Test): TestTaskFilters {
        val filter = task.getFilter() as DefaultTestFilter
        val options = task.getOptions()
        return DefaultTestTaskFilters(
            filter.getIncludePatterns(),
            filter.getCommandLineIncludePatterns(),
            filter.getExcludePatterns(),
            getOrEmpty<String>(options, Function { obj: JUnitPlatformOptions? -> obj!!.includeTags }),
            getOrEmpty<String>(options, Function { obj: JUnitPlatformOptions? -> obj!!.excludeTags }),
            getOrEmpty<String>(options, Function { obj: JUnitPlatformOptions? -> obj!!.includeEngines }),
            getOrEmpty<String>(options, Function { obj: JUnitPlatformOptions? -> obj!!.excludeEngines })
        )
    }

    private fun <T> getOrEmpty(options: TestFrameworkOptions, extractor: Function<JUnitPlatformOptions, MutableSet<T?>>): MutableSet<T?> {
        return if (options is JUnitPlatformOptions)
            extractor.apply(options)
        else
            ImmutableSet.of<T?>()
    }

    private fun collectForkOptions(task: Test): TestTaskForkOptions {
        val testIsModule: Boolean = javaModuleDetector.isModule(task.modularity.getInferModulePath().get(), task.testClassesDirs)
        val forkOptions: JavaForkOptions? = forkOptionsFactory.newJavaForkOptions()
        task.copyTo(forkOptions!!)
        val executable = forkOptions.getExecutable()
        return DefaultTestTaskForkOptions(
            forkOptions.getWorkingDir(),
            executable,
            detectJavaVersion(executable),
            javaModuleDetector.inferClasspath(testIsModule, task.classpath),
            javaModuleDetector.inferModulePath(testIsModule, task.classpath),
            forkOptions.getAllJvmArgs(),
            DefaultProcessForkOptions.getActualEnvironment(forkOptions)
        )
    }

    private fun detectJavaVersion(executable: String): Int {
        return jvmVersionDetector.getJavaVersionMajor(executable)
    }
}
