/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.text.TreeFormatter
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.stream.Collectors
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

internal object SimpleGeneratedJavaClassCompiler {
    /**
     * Compiles generated Java source files.
     *
     * @param srcDir where the compiler will output the sources
     * @param dstDir where the compiler will output the class files
     * @param classes the classes to compile
     * @param classPath the classpath to use for compilation
     */
    @Throws(GeneratedClassCompilationException::class)
    fun compile(srcDir: File, dstDir: File, classes: MutableList<ClassSource>, classPath: ClassPath) {
        val compiler = ToolProvider.getSystemJavaCompiler()
        if (compiler == null) {
            throw GeneratedClassCompilationException("No Java compiler found, please ensure you are running Gradle with a JDK")
        }
        val ds = DiagnosticCollector<JavaFileObject>()
        try {
            compiler.getStandardFileManager(ds, null, null).use { mgr ->
                val options = buildOptions(dstDir, classPath)
                val filesToCompile = outputSourceFilesToSourceDir(srcDir, classes)
                if (dstDir.exists() || dstDir.mkdirs()) {
                    val sources = mgr.getJavaFileObjectsFromFiles(filesToCompile)
                    val task = compiler.getTask(null, mgr, ds, options, null, sources)
                    task.call()
                } else {
                    throw GeneratedClassCompilationException("Unable to create output classes directory")
                }
            }
        } catch (e: IOException) {
            throw GeneratedClassCompilationException("Unable to compile generated classes", e)
        }
        val diagnostics = ds.getDiagnostics().stream()
            .filter { d: Diagnostic<out JavaFileObject?>? -> d!!.getKind() == Diagnostic.Kind.ERROR }
            .collect(Collectors.toList())
        if (!diagnostics.isEmpty()) {
            throwCompilationError(diagnostics)
        }
    }

    private fun throwCompilationError(diagnostics: MutableList<Diagnostic<out JavaFileObject>>) {
        val formatter = TreeFormatter()
        formatter.node("Unable to compile generated sources")
        formatter.startChildren()
        for (d in diagnostics) {
            val source: JavaFileObject = d.getSource()
            val srcFile = if (source == null) "unknown" else File(source.toUri()).getName()
            val diagLine = String.format("File %s, line: %d, %s", srcFile, d.getLineNumber(), d.getMessage(null))
            formatter.node(diagLine)
        }
        formatter.endChildren()
        throw GeneratedClassCompilationException(formatter.toString())
    }

    @Throws(IOException::class)
    private fun outputSourceFilesToSourceDir(srcDir: File, classes: MutableList<ClassSource>): MutableList<File> {
        val filesToCompile: MutableList<File> = ArrayList<File>(classes.size)
        for (classSource in classes) {
            val packageName = classSource.getPackageName()
            val className = classSource.getSimpleClassName()
            val classCode = classSource.getSource()
            val file = sourceFile(srcDir, packageName, className)
            writeSourceFile(classCode, file)
            filesToCompile.add(file)
        }
        return filesToCompile
    }

    @Throws(IOException::class)
    private fun writeSourceFile(classCode: String, file: File) {
        file.getParentFile().mkdirs()
        Files.write(file.toPath(), classCode.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sourceFile(srcDir: File, packageName: String, className: String): File {
        return File(srcDir, packageName.replace('.', '/') + "/" + className + ".java")
    }

    private fun buildOptions(dstDir: File, classPath: ClassPath): MutableList<String> {
        val options: MutableList<String> = ArrayList<String>()
        options.add("-source")
        options.add("1.8")
        options.add("-target")
        options.add("1.8")
        options.add("-classpath")
        val cp = classPath.getAsFiles().stream().map<String> { obj: File? -> obj!!.getAbsolutePath() }.collect(Collectors.joining(File.pathSeparator))
        options.add(cp)
        options.add("-d")
        options.add(dstDir.getAbsolutePath())
        return options
    }
}
