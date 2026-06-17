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

import org.codehaus.groovy.ast.ClassNode
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.SyncSpec
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.Actions
import javax.inject.Inject

@CacheableTask
abstract class ExtractPluginRequestsTask : DefaultTask() {
    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations?

    @get:Inject
    protected abstract val classLoaderScopeRegistry: ClassLoaderScopeRegistry?

    @get:Inject
    protected abstract val scriptCompilationHandler: ScriptCompilationHandler?

    @get:Inject
    protected abstract val compileOperationFactory: CompileOperationFactory?

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val scriptFiles: ConfigurableFileCollection?

    @get:OutputDirectory
    abstract val extractedPluginRequestsClassesDirectory: DirectoryProperty?

    @get:OutputDirectory
    abstract val extractedPluginRequestsClassesStagingDirectory: DirectoryProperty?

    @get:Internal
    abstract val scriptPlugins: ListProperty<PrecompiledGroovyScript>?

    @TaskAction
    fun extractPluginsBlocks() {
        this.fileSystemOperations.delete(Action { spec: DeleteSpec? -> spec!!.delete(this.extractedPluginRequestsClassesDirectory) })
        this.extractedPluginRequestsClassesDirectory.get().getAsFile().mkdirs()

        // TODO: Use worker API?
        for (scriptPlugin in this.scriptPlugins.get()) {
            compilePluginsBlock(scriptPlugin)
        }
    }

    private fun compilePluginsBlock(scriptPlugin: PrecompiledGroovyScript) {
        val classLoaderScope = this.classLoaderScopeRegistry.getCoreAndPluginsScope()
        val pluginsCompileOperation = this.compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget())
        val outputDir = this.extractedPluginRequestsClassesDirectory.get().dir(scriptPlugin.getId()).getAsFile()
        this.scriptCompilationHandler.compileToDir(
            scriptPlugin.getFirstPassSource(), classLoaderScope.getExportClassLoader(), outputDir, outputDir, pluginsCompileOperation,
            FirstPassPrecompiledScript::class.java, Actions.doNothing<ClassNode>()
        )

        this.fileSystemOperations.sync(Action { copySpec: SyncSpec? ->
            copySpec!!.from(this.extractedPluginRequestsClassesDirectory.getAsFileTree().getFiles()).include("**.class")
            copySpec.into(this.extractedPluginRequestsClassesStagingDirectory)
        })
    }
}
