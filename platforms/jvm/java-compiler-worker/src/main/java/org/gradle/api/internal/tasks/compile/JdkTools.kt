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
package org.gradle.api.internal.tasks.compile

import com.sun.source.util.JavacTask
import com.sun.tools.javac.api.JavacTool
import com.sun.tools.javac.util.Context
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantsAnalysisResult
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.reflect.DirectInstantiator
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.nio.charset.Charset
import java.util.Locale
import java.util.Optional
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import javax.lang.model.SourceVersion
import javax.tools.DiagnosticListener
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.StandardJavaFileManager

/**
 * Subset replacement for [javax.tools.ToolProvider] that avoids the application class loader.
 */
class JdkTools internal constructor(compilerPlugins: MutableList<File?>?) {
    private val isolatedToolsLoader: ClassLoader

    private var incrementalCompileTaskClass: Class<JavaCompiler.CompilationTask?>? = null

    init {
        val defaultClassLoaderFactory = DefaultClassLoaderFactory()
        val filteringClassLoader = getSystemFilteringClassLoader(defaultClassLoaderFactory)
        isolatedToolsLoader = VisitableURLClassLoader.fromClassPath("jdk-tools", filteringClassLoader, DefaultClassPath.of(compilerPlugins))
    }

    private fun getSystemFilteringClassLoader(classLoaderFactory: ClassLoaderFactory): ClassLoader {
        val filterSpec = FilteringClassLoader.Spec()
        filterSpec.allowPackage("com.sun.tools")
        filterSpec.allowPackage("com.sun.source")
        return classLoaderFactory.createFilteringClassLoader(
            this.javaClass.getClassLoader(),
            filterSpec
        )
    }

    val systemJavaCompiler: ContextAwareJavaCompiler
        get() = JdkTools.DefaultIncrementalAwareCompiler(buildJavaCompiler())

    private fun buildJavaCompiler(): JavacTool {
        return JavacTool.create()
    }

    private inner class DefaultIncrementalAwareCompiler(private val delegate: JavacTool) : IncrementalCompilationAwareJavaCompiler {
        override fun getTask(
            out: Writer?,
            fileManager: JavaFileManager?,
            diagnosticListener: DiagnosticListener<in JavaFileObject?>?,
            options: Iterable<String?>?,
            classes: Iterable<String?>?,
            compilationUnits: Iterable<out JavaFileObject?>?
        ): JavaCompiler.CompilationTask? {
            return delegate.getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits)
        }

        override fun getTask(
            out: Writer?,
            fileManager: JavaFileManager?,
            diagnosticListener: DiagnosticListener<in JavaFileObject?>?,
            options: Iterable<String?>?,
            classes: Iterable<String?>?,
            compilationUnits: Iterable<out JavaFileObject?>?,
            context: Context
        ): JavacTask {
            return delegate.getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits, context)
        }

        override fun getStandardFileManager(diagnosticListener: DiagnosticListener<in JavaFileObject?>?, locale: Locale?, charset: Charset?): StandardJavaFileManager {
            return delegate.getStandardFileManager(diagnosticListener, locale, charset)
        }

        override fun name(): String {
            return delegate.name()
        }

        override fun run(`in`: InputStream?, out: OutputStream?, err: OutputStream?, vararg arguments: String?): Int {
            return delegate.run(`in`, out, err, *arguments)
        }

        override fun getSourceVersions(): MutableSet<SourceVersion?>? {
            return delegate.getSourceVersions()
        }

        override fun isSupportedOption(option: String?): Int {
            return delegate.isSupportedOption(option)
        }

        override fun makeIncremental(
            task: JavaCompiler.CompilationTask, sourceToClassMapping: MutableMap<String?, MutableSet<String?>?>,
            constantsAnalysisResult: ConstantsAnalysisResult, compilationSourceDirs: CompilationSourceDirs,
            classBackupService: CompilationClassBackupService
        ): JavaCompiler.CompilationTask {
            ensureCompilerTask()
            // task (JavacTaskImpl) classloader: app classloader
            // incrementalCompileTaskClass classloader: jdk-tools
            return DirectInstantiator.instantiate<JavaCompiler.CompilationTask>(
                incrementalCompileTaskClass, task,
                Function { sourceFile: File? -> compilationSourceDirs.relativize(sourceFile!!) } as Function<File?, Optional<String?>?>,
                Consumer { classFqName: String? -> classBackupService.maybeBackupClassFile(classFqName) },
                Consumer { m: MutableMap<String?, MutableSet<String?>?>? -> sourceToClassMapping.putAll(m!!) },
                BiConsumer { constantOrigin: String?, constantDependent: String? -> constantsAnalysisResult.addPublicDependent(constantOrigin, constantDependent) },
                BiConsumer { constantOrigin: String?, constantDependent: String? -> constantsAnalysisResult.addPrivateDependent(constantOrigin, constantDependent) }
            )
        }
    }

    private fun ensureCompilerTask() {
        if (incrementalCompileTaskClass == null) {
            synchronized(this) {
                try {
                    incrementalCompileTaskClass = uncheckedCast<Class<JavaCompiler.CompilationTask?>?>(isolatedToolsLoader.loadClass("org.gradle.internal.compiler.java.IncrementalCompileTask"))
                } catch (e: ClassNotFoundException) {
                    throw throwAsUncheckedException(e)
                }
            }
        }
    }
}
