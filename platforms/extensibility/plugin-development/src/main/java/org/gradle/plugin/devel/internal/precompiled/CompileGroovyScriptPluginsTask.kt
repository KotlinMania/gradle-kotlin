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

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.SyncSpec
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClassLoaderUtils
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier
import java.io.File
import java.net.URLClassLoader
import javax.inject.Inject

@CacheableTask
internal abstract class CompileGroovyScriptPluginsTask : DefaultTask() {
    private val intermediatePluginClassesDirectory: Provider<Directory>
    private val intermediatePluginMetadataDirectory: Provider<Directory>

    init {
        val buildDir = this.projectLayout.getBuildDirectory()
        this.intermediatePluginClassesDirectory = buildDir.dir("groovy-dsl-plugins/work/classes")
        this.intermediatePluginMetadataDirectory = buildDir.dir("groovy-dsl-plugins/work/metadata")
    }

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    protected abstract val classLoaderScopeRegistry: ClassLoaderScopeRegistry?

    @get:Inject
    protected abstract val scriptCompilationHandler: ScriptCompilationHandler?

    @get:Inject
    protected abstract val compileOperationFactory: CompileOperationFactory?

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val scriptFiles: ConfigurableFileCollection?

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection?

    @get:OutputDirectory
    abstract val precompiledGroovyScriptsOutputDirectory: DirectoryProperty?

    @get:Internal
    abstract val scriptPlugins: ListProperty<PrecompiledGroovyScript>?

    @TaskAction
    fun compileScripts() {
        val classLoaderScope = this.classLoaderScopeRegistry.getCoreAndPluginsScope()
        val compileClassLoader: ClassLoader = URLClassLoader(DefaultClassPath.of(this.classpath).getAsURLArray(), classLoaderScope.getLocalClassLoader())
        val fileSystemOperations = this.fileSystemOperations
        fileSystemOperations.delete(Action { spec: DeleteSpec? -> spec!!.delete(intermediatePluginMetadataDirectory, intermediatePluginClassesDirectory) })
        intermediatePluginMetadataDirectory.get().getAsFile().mkdirs()
        intermediatePluginClassesDirectory.get().getAsFile().mkdirs()

        // TODO: Use worker API?
        for (scriptPlugin in this.scriptPlugins.get()) {
            compileBuildScript(scriptPlugin, compileClassLoader)
        }

        fileSystemOperations.sync(Action { copySpec: SyncSpec? ->
            copySpec!!.from(intermediatePluginClassesDirectory.get().getAsFileTree().getFiles())
            copySpec.into(this.precompiledGroovyScriptsOutputDirectory)
        })
        ClassLoaderUtils.tryClose(compileClassLoader)
    }

    private fun compileBuildScript(scriptPlugin: PrecompiledGroovyScript, compileClassLoader: ClassLoader) {
        val target = scriptPlugin.getScriptTarget()
        val scriptCompileOperation = this.compileOperationFactory.getScriptCompileOperation(scriptPlugin.getBodySource(), target)
        val scriptMetadataDir: File = subdirectory(intermediatePluginMetadataDirectory, scriptPlugin.getId())
        val scriptClassesDir: File = subdirectory(intermediatePluginClassesDirectory, scriptPlugin.getId())
        this.scriptCompilationHandler.compileToDir(
            scriptPlugin.getBodySource(), compileClassLoader, scriptClassesDir,
            scriptMetadataDir, scriptCompileOperation, target.getScriptClass(),
            ClosureCreationInterceptingVerifier.INSTANCE
        )
    }

    companion object {
        private fun subdirectory(root: Provider<Directory>, subdirPath: String): File {
            return root.get().dir(subdirPath).getAsFile()
        }
    }
}
