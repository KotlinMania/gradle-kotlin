/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.catalog.internal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.io.UnsupportedEncodingException

@CacheableTask
abstract class TomlFileGenerator : DefaultTask() {
    @get:Input
    abstract val dependenciesModel: Property<DefaultVersionCatalog>?

    @get:OutputFile
    abstract val outputFile: RegularFileProperty?

    @TaskAction
    @Throws(IOException::class)
    fun generateToml() {
        val model = this.dependenciesModel.get()
        val outputFile = this.outputFile.getAsFile().get()
        val outputDir = outputFile.getParentFile()
        if (outputDir.exists() || outputFile.mkdirs()) {
            doGenerate(model, outputFile)
        } else {
            throw GradleException("Unable to generate TOML dependencies file into " + outputDir)
        }
    }

    @Throws(FileNotFoundException::class, UnsupportedEncodingException::class)
    private fun doGenerate(model: DefaultVersionCatalog, outputFile: File) {
        PrintWriter(outputFile, "UTF-8").use { writer ->
            val ctx = TomlWriter(writer)
            ctx.generate(model)
        }
    }
}
