/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Transformer
import org.gradle.api.XmlProvider
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.tasks.internal.RelativeFileNameTransformer
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioFiltersFile
import org.gradle.internal.serialization.Cached
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Task for generating a Visual Studio filters file (e.g. `foo.vcxproj.filters`).
 */
@Incubating
@DisableCachingByDefault(because = "Not made cacheable, yet")
abstract class GenerateFiltersFileTask @Inject constructor(visualStudioProject: DefaultVisualStudioProject?) : XmlGeneratorTask<VisualStudioFiltersFile?>() {
    @Transient
    private var visualStudioProject: DefaultVisualStudioProject? = null
    private val outputFile = getProject().provider<File?>(SerializableLambdas.callable<File?>(SerializableLambdas.SerializableCallable { visualStudioProject!!.getFiltersFile().getLocation() }))
    private val spec = Cached.of({ this.calculateSpec() })

    init {
        setVisualStudioProject(visualStudioProject)
    }

    val incremental: Boolean
        get() = true

    fun setVisualStudioProject(vsProject: VisualStudioProject?) {
        this.visualStudioProject = vsProject as DefaultVisualStudioProject
    }

    @Internal
    fun getVisualStudioProject(): VisualStudioProject {
        return visualStudioProject!!
    }

    @get:Incubating
    @get:Nested
    protected val filterSpec: FiltersSpec?
        /**
         * Returns the [FiltersSpec] for this task.
         *
         * @since 8.11
         */
        get() = spec.get()

    val inputFile: File?
        get() = null

    public override fun getOutputFile(): File? {
        return outputFile.get()
    }

    override fun configure(filtersFile: VisualStudioFiltersFile) {
        val spec = this.spec.get()

        for (sourceFile in spec.sourceFiles) {
            filtersFile.addSource(sourceFile)
        }

        for (headerFile in spec.headerFiles) {
            filtersFile.addHeader(headerFile)
        }

        for (xmlAction in spec!!.actions) {
            xmlTransformer.addAction(xmlAction)
        }
    }

    override fun create(): VisualStudioFiltersFile? {
        return VisualStudioFiltersFile(xmlTransformer, spec.get().fileNameTransformer)
    }

    private fun calculateSpec(): FiltersSpec {
        return GenerateFiltersFileTask.FiltersSpec(
            visualStudioProject!!.getSourceFiles(),
            visualStudioProject!!.getHeaderFiles(),
            visualStudioProject!!.getFiltersFile().getXmlActions(),
            RelativeFileNameTransformer.Companion.forFile(getProject().getRootDir(), visualStudioProject!!.getFiltersFile().getLocation())
        )
    }

    /**
     * The data to use to generate the filters file.
     *
     * @since 8.11
     */
    @Incubating
    protected class FiltersSpec private constructor(
        private val sourceFiles: FileCollection, private val headerFiles: FileCollection,
        /**
         * Additional XML generation actions.
         *
         * @since 8.11
         */
        @get:Nested @get:Incubating val actions: MutableList<Action<in XmlProvider?>?>, private val fileNameTransformer: Transformer<String?, File?>
    ) {
        @get:Incubating
        @get:Input
        val sourceFilePaths: Provider<MutableSet<String?>?>
            /**
             * The source files to include in the filter.
             *
             * @since 8.11
             */
            get() = sourceFiles.getElements()
                .map<MutableSet<String?>?>(Transformer { files: MutableSet<FileSystemLocation?>? ->
                    collect(
                        files,
                        { file -> file.getAsFile().getAbsolutePath() })
                })

        @get:Incubating
        @get:Input
        val headerFilesPaths: Provider<MutableSet<String?>?>
            /**
             * The header files to include in the filter.
             *
             * @since 8.11
             */
            get() = headerFiles.getElements()
                .map<MutableSet<String?>?>(Transformer { files: MutableSet<FileSystemLocation?>? ->
                    collect(
                        files,
                        { file -> file.getAsFile().getAbsolutePath() })
                })
    }
}
