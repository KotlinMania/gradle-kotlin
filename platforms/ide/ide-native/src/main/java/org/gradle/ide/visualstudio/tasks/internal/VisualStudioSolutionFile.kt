/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.tasks.internal

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.Action
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.ide.visualstudio.TextProvider
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.plugins.ide.internal.generator.AbstractPersistableConfigurationObject
import org.gradle.util.internal.TextUtil
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.Scanner
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

class VisualStudioSolutionFile : AbstractPersistableConfigurationObject() {
    val actions: MutableList<Action<in TextProvider>> = ArrayList<Action<in TextProvider>>()
    private val projects: MutableMap<File, String> = LinkedHashMap<File, String>()
    private val projectConfigurations: MutableMap<File, MutableSet<ConfigurationSpec>> = LinkedHashMap<File, MutableSet<ConfigurationSpec>>()

    private var baseText: String? = null

    val defaultResourceName: String
        get() = "default.sln"

    fun setProjects(projects: MutableList<ProjectSpec>) {
        for (project in projects) {
            this.projects.put(project.projectFile, project.name)
            val configs = projectConfigurations.computeIfAbsent(project.projectFile) { f: File? -> HashSet<ConfigurationSpec?>() }
            configs.addAll(project.configurations)
        }
    }

    @Throws(Exception::class)
    public override fun load(inputStream: InputStream) {
        Scanner(inputStream, StandardCharsets.UTF_8.name()).use { scanner ->
            baseText = scanner.useDelimiter("\\A").next()
        }
    }

    public override fun store(outputStream: OutputStream) {
        val provider = SimpleTextProvider()
        generateContent(provider.asBuilder())
        for (action in actions) {
            action.execute(provider)
        }
        val text = TextUtil.convertLineSeparators(provider.getText(), TextUtil.getWindowsLineSeparator())
        val writer: Writer = OutputStreamWriter(outputStream)
        try {
            writer.write(text)
            writer.flush()
        } catch (e: IOException) {
            throw throwAsUncheckedException(e, true)
        }
    }

    private fun generateContent(builder: StringBuilder) {
        builder.append(baseText)
        for (project in projects.entries) {
            val projectFile = project.key
            val projectName = project.value
            builder.append(
                "\n" +
                        "Project(\"{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}\") = \"" + projectName + "\", \"" + projectFile.getAbsolutePath() + "\", \"" + DefaultVisualStudioProject.Companion.getUUID(
                    projectFile
                ) + "\"\n" +
                        "EndProject"
            )
        }
        builder.append(
            "\n" +
                    "Global\n" +
                    "\tGlobalSection(SolutionConfigurationPlatforms) = preSolution"
        )

        val configurationNames: MutableSet<String> = projectConfigurations.values.stream()
            .flatMap<String> { set: MutableSet<ConfigurationSpec?>? -> set!!.stream().map<String> { spec: ConfigurationSpec? -> spec!!.name } }
            .collect(Collectors.toCollection(Supplier { TreeSet() }))
        for (configurationName in configurationNames) {
            builder.append("\n\t\t").append(configurationName).append(" = ").append(configurationName)
        }
        builder.append(
            "\n" +
                    "\tEndGlobalSection\n" +
                    "\tGlobalSection(ProjectConfigurationPlatforms) = postSolution"
        )
        for (projectFile in projects.keys) {
            val configurations = configurationNames.stream()
                .flatMap<String> { configurationName: String? ->
                    val result: MutableList<String> = ArrayList<String>()
                    val configuration = projectConfigurations.get(projectFile)!!.stream()
                        .filter { spec: ConfigurationSpec? -> spec!!.name == configurationName }
                        .findFirst().orElse(null)
                    val lastConfiguration = projectConfigurations.get(projectFile)!!.stream()
                        .sorted(Comparator.comparing<ConfigurationSpec, String>(Function { obj: ConfigurationSpec -> obj.name }))
                        .reduce { first: ConfigurationSpec?, second: ConfigurationSpec? -> second }.orElse(null)
                    if (configuration == null) {
                        result.add(configurationName + ".ActiveCfg = " + lastConfiguration!!.name)
                    } else {
                        result.add(configurationName + ".ActiveCfg = " + configuration.name)
                        if (configuration.buildable) {
                            result.add(configurationName + ".Build.0 = " + configuration.name)
                        }
                    }
                    result.stream()
                }
                .collect(Collectors.toList())

            for (configuration in configurations) {
                builder.append("\n\t\t").append(DefaultVisualStudioProject.Companion.getUUID(projectFile)).append(".").append(configuration)
            }
        }

        builder.append(
            "\n" +
                    "\tEndGlobalSection\n" +
                    "\tGlobalSection(SolutionProperties) = preSolution\n" +
                    "\t\tHideSolutionNode = FALSE\n" +
                    "\tEndGlobalSection\n" +
                    "EndGlobal\n"
        )
    }

    private class SimpleTextProvider : TextProvider {
        private val builder = StringBuilder()

        override fun asBuilder(): StringBuilder {
            return builder
        }

        override fun getText(): String {
            return builder.toString()
        }

        override fun setText(value: String) {
            builder.replace(0, builder.length, value)
        }
    }

    class ConfigurationSpec(@get:Input val name: String, @get:Input val buildable: Boolean)

    class ProjectSpec(@get:Input val name: String, @field:VisibleForTesting val projectFile: File, @get:Nested val configurations: MutableList<ConfigurationSpec>) {
        @get:Input
        val projectFilePath: String
            get() = projectFile.getAbsolutePath()
    }
}
