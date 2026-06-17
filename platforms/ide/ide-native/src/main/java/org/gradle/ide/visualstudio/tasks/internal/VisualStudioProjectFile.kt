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
package org.gradle.ide.visualstudio.tasks.internal

import groovy.util.Node
import org.gradle.api.Transformer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.Collections
import java.util.Objects
import java.util.stream.Collectors

class VisualStudioProjectFile(xmlTransformer: XmlTransformer, private val fileLocationResolver: Transformer<String, File>) : XmlPersistableConfigurationObject(xmlTransformer) {
    private var gradleCommand = "gradle"
    private var visualStudioVersion: VersionNumber? = null

    val defaultResourceName: String
        get() = "default.vcxproj"

    fun setGradleCommand(gradleCommand: String) {
        this.gradleCommand = gradleCommand
    }

    fun setProjectUuid(uuid: String) {
        getPropertyGroupForLabel("Globals").appendNode("ProjectGUID", uuid)
    }

    fun setVisualStudioVersion(version: VersionNumber) {
        visualStudioVersion = version
        xml!!.attributes().put("ToolsVersion", if (version.getMajor() >= 12) version.getMajor().toString() + ".0" else "4.0")
    }

    fun setSdkVersion(version: VersionNumber) {
        getPropertyGroupForLabel("Globals").appendNode(
            "WindowsTargetPlatformVersion",
            if (version.getMicro() != 0) version else version.getMajor().toString() + "." + version.getMinor()
        )
    }

    fun addSourceFile(file: File) {
        getItemGroupForLabel("Sources").appendNode(
            "ClCompile",
            Collections.singletonMap<String, String>("Include", toPath(file))
        )
    }

    fun addResource(file: File) {
        getItemGroupForLabel("References").appendNode(
            "ResourceCompile",
            Collections.singletonMap<String, String>("Include", toPath(file))
        )
    }

    fun addHeaderFile(file: File) {
        getItemGroupForLabel("Headers").appendNode(
            "ClInclude",
            Collections.singletonMap<String, String>("Include", toPath(file))
        )
    }

    fun addConfiguration(configuration: ConfigurationSpec) {
        val configNode = getItemGroupForLabel("ProjectConfigurations").appendNode(
            "ProjectConfiguration",
            Collections.singletonMap<String, String>("Include", configuration.name)
        )
        configNode.appendNode("Configuration", configuration.configurationName)
        configNode.appendNode("Platform", configuration.platformName)
        val configCondition = "'$(Configuration)|$(Platform)'=='" + configuration.name + "'"

        val vsOutputDir = ".vs\\" + configuration.projectName + "\\$(Configuration)\\"
        val configGroup: Node = appendDirectSibling(
            getImportsForProject("$(VCTargetsPath)\\Microsoft.Cpp.Default.props"),
            "PropertyGroup",
            object : LinkedHashMap<String, String>() {
                init {
                    put("Label", "Configuration")
                    put("Condition", configCondition)
                }
            }
        )
        configGroup.appendNode("ConfigurationType", configuration.type)
        if (configuration.isBuildable) {
            configGroup.appendNode("UseDebugLibraries", configuration.isDebuggable)
            configGroup.appendNode("OutDir", vsOutputDir)
            configGroup.appendNode("IntDir", vsOutputDir)
        }
        if (visualStudioVersion!!.getMajor() > 14) {
            configGroup.appendNode("PlatformToolset", "v141")
        } else if (visualStudioVersion!!.getMajor() >= 11) {
            configGroup.appendNode("PlatformToolset", "v" + visualStudioVersion!!.getMajor() + "0")
        }

        val includePath = java.lang.String.join(";", toPath(if (configuration.isBuildable) configuration.includeDirs else mutableSetOf<File>()))
        val nMakeGroup: Node = appendDirectSibling(
            getPropertyGroupForLabel("UserMacros"),
            "PropertyGroup",
            object : LinkedHashMap<String, String>() {
                init {
                    put("Label", "NMakeConfiguration")
                    put("Condition", configCondition)
                }
            }
        )
        if (configuration.isBuildable) {
            nMakeGroup.appendNode("NMakeBuildCommandLine", gradleCommand + " " + configuration.buildTaskPath)
            nMakeGroup.appendNode("NMakeCleanCommandLine", gradleCommand + " " + configuration.cleanTaskPath)
            nMakeGroup.appendNode("NMakeReBuildCommandLine", gradleCommand + " " + configuration.cleanTaskPath + " " + configuration.buildTaskPath)
            nMakeGroup.appendNode("NMakePreprocessorDefinitions", java.lang.String.join(";", configuration.compilerDefines))
            nMakeGroup.appendNode("NMakeIncludeSearchPath", includePath)
            nMakeGroup.appendNode("NMakeOutput", toPath(configuration.outputFile!!))
        } else {
            val errorCommand = "echo '" + configuration.projectName + "' project is not buildable. && exit /b -42"
            nMakeGroup.appendNode("NMakeBuildCommandLine", errorCommand)
            nMakeGroup.appendNode("NMakeCleanCommandLine", errorCommand)
            nMakeGroup.appendNode("NMakeReBuildCommandLine", errorCommand)
        }

        if (configuration.languageStandard != null && configuration.languageStandard != VisualStudioTargetBinary.LanguageStandard.NONE) {
            xml!!.appendNode("ItemDefinitionGroup", Collections.singletonMap<String, String>("Condition", configCondition))
                .appendNode("ClCompile")
                .appendNode("LanguageStandard", configuration.languageStandard.getValue())
        }
    }

    private fun getItemGroupForLabel(label: String): Node {
        return getSingleNodeWithAttribute("ItemGroup", "Label", label)
    }

    private fun getPropertyGroupForLabel(label: String): Node {
        return getSingleNodeWithAttribute("PropertyGroup", "Label", label)
    }

    private fun getImportsForProject(project: String): Node {
        return getSingleNodeWithAttribute("Import", "Project", project)
    }

    private fun getSingleNodeWithAttribute(nodeName: String, attributeName: String, attributeValue: String): Node {
        return Objects.requireNonNull(
            findFirstChildWithAttributeValue(xml, nodeName, attributeName, attributeValue),
            "No '" + nodeName + "' with attribute '" + attributeName + " = " + attributeValue + "' found"
        )!!
    }

    private fun toPath(files: MutableSet<File>): MutableList<String> {
        return files.stream().map<String> { file: File? -> this.toPath(file!!) }.collect(Collectors.toList())
    }

    private fun toPath(file: File): String {
        return fileLocationResolver.transform(file)
    }

    class ConfigurationSpec(
        @get:Input val name: String,
        @get:Input val configurationName: String,
        @get:Input val projectName: String,
        @get:Input val platformName: String,
        @get:Input val type: String,
        @get:Input val isBuildable: Boolean,
        @get:Input val isDebuggable: Boolean,
        private val includeDirs: MutableSet<File>,
        @get:Input @get:Optional val buildTaskPath: String?,
        @get:Input @get:Optional val cleanTaskPath: String?,
        @get:Input val compilerDefines: MutableList<String>,
        private val outputFile: File?,
        @get:Input @get:Optional val languageStandard: VisualStudioTargetBinary.LanguageStandard?
    ) {
        @get:Input
        val includeDirPaths: MutableCollection<String>
            get() = includeDirs.stream().map<String> { obj: File? -> obj!!.getAbsolutePath() }.collect(Collectors.toList())

        @get:Optional
        @get:Input
        val outputFilePath: String?
            get() {
                if (outputFile != null) {
                    return outputFile.getAbsolutePath()
                }
                return null
            }
    }

    companion object {
        /**
         * Replicates the behaviour of [Node.plus].
         */
        private fun appendDirectSibling(node: Node, siblingName: String, siblingAttributes: LinkedHashMap<String, String>): Node {
            if (node.parent() == null) {
                throw UnsupportedOperationException("Adding sibling nodes to the root node is not supported")
            }
            // Grab tail
            val parentChildren = node.parent().children()
            val afterIndex = parentChildren.indexOf(node)
            val tail: MutableList<*> = ArrayList<Any?>(parentChildren.subList(afterIndex + 1, parentChildren.size))
            parentChildren.subList(afterIndex + 1, parentChildren.size).clear()
            // Add sibling
            val sibling = node.parent().appendNode(siblingName, siblingAttributes)
            // Restore tail
            node.parent().children().addAll(tail)
            return sibling
        }
    }
}
