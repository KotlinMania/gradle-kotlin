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
package org.gradle.plugin.devel.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.Deleter.ensureEmptyDirectory
import org.gradle.internal.util.PropertiesUtils
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Generates plugin descriptors from plugin declarations.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class GeneratePluginDescriptors : DefaultTask() {
    /**
     * Returns all `(id, implementation class)` pairs from [.getDeclarations].
     *
     *
     * This map is the only input needed from the plugin declarations to create the plugin descriptors.
     */
    @get:Input
    val implementationClassById: Provider<MutableMap<String, String>>

    init {
        implementationClassById = this.declarations.map<MutableMap<String, String>>(Transformer { declarations: MutableList<PluginDeclaration?>? ->
            declarations!!.stream()
                .collect(
                    Collectors.toMap(
                        Function { obj: PluginDeclaration? -> obj!!.getId() },
                        Function { obj: PluginDeclaration? -> obj!!.getImplementationClass() },
                        BinaryOperator { a: String?, b: String? -> b },
                        Supplier { LinkedHashMap() })
                )
        }
        )
    }

    @get:Internal("Changes for the declarations are tracked via implementationClassById")
    abstract val declarations: ListProperty<PluginDeclaration>?

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty?

    @TaskAction
    fun generatePluginDescriptors() {
        val outputDir = this.outputDirectory.get().getAsFile()
        clearOutputDirectory(outputDir)
        for (entry in implementationClassById.get().entries) {
            val id = entry.key
            val implementationClass = entry.value
            val descriptorFile = File(outputDir, id + ".properties")
            val properties = Properties()
            properties.setProperty("implementation-class", implementationClass)
            writePropertiesTo(properties, descriptorFile)
        }
    }

    @get:Inject
    protected abstract val deleter: Deleter?

    private fun clearOutputDirectory(directoryToClear: File) {
        try {
            this.deleter.ensureEmptyDirectory(directoryToClear)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private fun writePropertiesTo(properties: Properties, descriptorFile: File) {
        try {
            PropertiesUtils.store(properties, descriptorFile)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }
}
