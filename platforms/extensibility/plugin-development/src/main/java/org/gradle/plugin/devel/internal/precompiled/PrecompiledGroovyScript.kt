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
package org.gradle.plugin.devel.internal.precompiled

import com.google.common.base.CaseFormat
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.configuration.ScriptTarget
import org.gradle.groovy.scripts.DelegatingScriptSource
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.internal.DefaultPluginId
import java.io.File

internal class PrecompiledGroovyScript(scriptFile: File, resourceLoader: TextFileResourceLoader) {
    val firstPassSource: ScriptSource
    val bodySource: ScriptSource
    private val type: Type
    private val pluginId: PluginId
    val scriptTarget: ScriptTarget
    private val precompiledScriptClassName: String

    private enum class Type(private val targetClass: Class<*>, private val fileExtension: String) {
        PROJECT(ProjectInternal::class.java, SCRIPT_PLUGIN_EXTENSION),
        SETTINGS(SettingsInternal::class.java, ".settings" + SCRIPT_PLUGIN_EXTENSION),
        INIT(GradleInternal::class.java, ".init" + SCRIPT_PLUGIN_EXTENSION);

        fun toPluginId(fileName: String): PluginId {
            return DefaultPluginId.of(fileName.substring(0, fileName.lastIndexOf(fileExtension)))
        }

        companion object {
            fun getType(fileName: String): Type {
                if (fileName.endsWith(Type.SETTINGS.fileExtension)) {
                    return Type.SETTINGS
                }
                if (fileName.endsWith(Type.INIT.fileExtension)) {
                    return Type.INIT
                }
                return Type.PROJECT
            }
        }
    }

    init {
        val fileName = scriptFile.getName()
        this.type = Type.Companion.getType(fileName)
        this.pluginId = type.toPluginId(fileName)
        this.precompiledScriptClassName = toJavaIdentifier(kebabCaseToPascalCase(pluginId.getId().replace('.', '-')))
        this.firstPassSource = PrecompiledScriptPluginFirstPassSource(scriptFile, precompiledScriptClassName, resourceLoader)
        this.bodySource = PrecompiledScriptPluginSource(scriptFile, precompiledScriptClassName, resourceLoader)
        this.scriptTarget = PrecompiledScriptTarget(type != Type.INIT, type == Type.SETTINGS)
    }

    fun declarePlugin(pluginDeclaration: PluginDeclaration) {
        pluginDeclaration.setImplementationClass(this.pluginAdapterClassName)
        pluginDeclaration.setId(pluginId.getId())
    }

    val id: String
        get() = pluginId.getId()

    val pluginAdapterClassName: String
        get() = precompiledScriptClassName + "Plugin"

    val firstPassClassName: String
        get() = firstPassSource.getClassName()

    val bodyClassName: String
        get() = bodySource.getClassName()

    val contentHash: HashCode
        get() = bodySource.getResource().getContentHash()

    val fileName: String
        get() = firstPassSource.getFileName()

    val targetClassName: String
        get() = type.targetClass.getName()

    private open class PrecompiledScriptPluginSource(private val scriptFile: File, className: String, resourceLoader: TextFileResourceLoader) : DelegatingScriptSource(
        TextResourceScriptSource(
            resourceLoader.loadFile(
                "script",
                scriptFile
            )
        )
    ) {
        private val className: String

        init {
            this.className = "precompiled_" + className
        }

        override fun getClassName(): String {
            return className
        }

        override fun getFileName(): String {
            return scriptFile.getPath()
        }
    }

    private class PrecompiledScriptPluginFirstPassSource(scriptFile: File, className: String, resourceLoader: TextFileResourceLoader) :
        PrecompiledScriptPluginSource(scriptFile, className, resourceLoader) {
        override fun getClassName(): String {
            return "cp_" + super.getClassName()
        }
    }

    companion object {
        private const val SCRIPT_PLUGIN_EXTENSION = ".gradle"

        fun filterPluginFiles(patternFilterable: PatternFilterable) {
            patternFilterable.include("**/*" + SCRIPT_PLUGIN_EXTENSION)
        }

        private fun kebabCaseToPascalCase(s: String): String {
            return CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, s)
        }

        private fun toJavaIdentifier(s: String): String {
            val sb = StringBuilder()
            if (!Character.isJavaIdentifierStart(s.get(0))) {
                sb.append("_")
            }
            for (i in 0..<s.length) {
                val c = s.get(i)
                if (Character.isJavaIdentifierPart(c)) {
                    sb.append(c)
                } else {
                    sb.append("_")
                }
            }
            return sb.toString()
        }
    }
}
