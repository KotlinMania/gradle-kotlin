/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.tasks.compile.CompileOptions
import java.io.File
import java.util.Arrays

open class DefaultJavaCompileSpec : DefaultJvmLanguageCompileSpec(), JavaCompileSpec {
    private var compileOptions: MinimalJavaCompileOptions? = null
    private var annotationProcessorPath: MutableList<File?>? = null
    private var effectiveAnnotationProcessors: MutableSet<AnnotationProcessorDeclaration?>? = null
    private var classesToProcess: MutableSet<String?>? = null
    private var modulePath: MutableList<File?>? = null
    private var sourceRoots: MutableList<File?>? = null
    private var classesToCompile: MutableSet<String?>? = mutableSetOf<String?>()
    private var backupDestinationDir: File? = null

    override fun getCompileOptions(): MinimalJavaCompileOptions {
        return compileOptions!!
    }

    override fun getClassBackupDir(): File? {
        return backupDestinationDir
    }

    override fun setClassBackupDir(classBackupDir: File?) {
        this.backupDestinationDir = classBackupDir
    }

    fun setCompileOptions(compileOptions: CompileOptions) {
        this.compileOptions = MinimalJavaCompileOptions(compileOptions)
    }

    override fun getAnnotationProcessorPath(): MutableList<File?>? {
        return annotationProcessorPath
    }

    override fun setAnnotationProcessorPath(annotationProcessorPath: MutableList<File?>?) {
        this.annotationProcessorPath = annotationProcessorPath
    }

    override fun getEffectiveAnnotationProcessors(): MutableSet<AnnotationProcessorDeclaration?>? {
        return effectiveAnnotationProcessors
    }

    override fun setEffectiveAnnotationProcessors(annotationProcessors: MutableSet<AnnotationProcessorDeclaration?>?) {
        this.effectiveAnnotationProcessors = annotationProcessors
    }

    override fun getClassesToProcess(): MutableSet<String?>? {
        return classesToProcess
    }

    override fun setClassesToProcess(classes: MutableSet<String?>?) {
        this.classesToProcess = classes
    }

    override fun setClassesToCompile(classes: MutableSet<String?>?) {
        this.classesToCompile = classes
    }

    override fun getClassesToCompile(): MutableSet<String?>? {
        return classesToCompile
    }

    override fun getModulePath(): MutableList<File?> {
        if (modulePath == null || modulePath!!.isEmpty()) {
            // This is kept for backward compatibility - may be removed in the future
            var i = 0
            val modulePaths: MutableList<String?> = ArrayList<String?>()
            // Some arguments can also be a GString, that is why use Object.toString()
            for (argObj in compileOptions!!.getCompilerArgs()) {
                val arg = argObj.toString()
                if ((arg == "--module-path" || arg == "-p") && (i + 1) < compileOptions!!.getCompilerArgs().size) {
                    val argValue: Any = compileOptions!!.getCompilerArgs().get(++i)
                    val modules: Array<String?> = argValue.toString().split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    modulePaths.addAll(Arrays.asList<String?>(*modules))
                } else if (arg.startsWith("--module-path=")) {
                    val modules: Array<String?> = arg.replace("--module-path=", "").split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    modulePaths.addAll(Arrays.asList<String?>(*modules))
                }
                i++
            }
            modulePath = modulePaths.stream()
                .map<File?> { pathname: String? -> File(pathname) }
                .collect(ImmutableList.toImmutableList<File?>())
        }
        return modulePath!!
    }

    override fun setModulePath(modulePath: MutableList<File?>?) {
        this.modulePath = modulePath
    }

    override fun getSourceRoots(): MutableList<File?>? {
        return sourceRoots
    }

    override fun setSourcesRoots(sourcesRoots: MutableList<File?>?) {
        this.sourceRoots = sourcesRoots
    }
}
