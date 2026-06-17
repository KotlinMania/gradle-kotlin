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
package org.gradle.api.tasks.scala.internal

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.RegularFile
import org.gradle.internal.process.ArgWriter
import org.gradle.workers.WorkAction
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import javax.inject.Inject

abstract class GenerateScaladoc : WorkAction<ScaladocParameters> {
    override fun execute() {
        val parameters = getParameters()
        val optionsFile = parameters.getOptionsFile().map<File?>(Transformer { obj: RegularFile? -> obj!!.getAsFile() }).map<Path?>(Transformer { obj: File? -> obj!!.toPath() }).getOrNull()
        try {
            this.fileSystemOperations.delete(Action { spec: DeleteSpec? -> spec!!.delete(parameters.getOutputDirectory()) })
            parameters.getOutputDirectory().get().getAsFile().mkdirs()

            val args = generateArgList(parameters, optionsFile)
            invokeScalaDoc(args, parameters.getIsScala3().get())
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Could not generate scaladoc", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Could not generate scaladoc", e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Could not generate scaladoc", e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Could not generate scaladoc", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Could not generate scaladoc", e)
        } finally {
            // Try to clean-up generated options file
            if (optionsFile != null) {
                optionsFile.toFile().delete()
            }
        }
    }

    private fun generateArgList(parameters: ScaladocParameters, optionsFile: Path?): MutableList<String?> {
        val args: MutableList<String?> = ArrayList<String?>()
        args.add("-d")
        args.add(parameters.getOutputDirectory().get().getAsFile().getAbsolutePath())

        args.add("-classpath")
        args.add(parameters.getClasspath().getAsPath())

        args.addAll(parameters.getOptions().get())

        val sourceFiles: MutableList<String?> = ArrayList<String?>()
        for (sourceFile in parameters.getSources().getAsFileTree().getFiles()) {
            sourceFiles.add(sourceFile.getAbsolutePath())
        }
        args.addAll(sourceFiles)

        if (optionsFile != null) {
            return ArgWriter.javaStyle().generateArgsFile(args, optionsFile.toFile())
        }

        return args
    }

    @Throws(ClassNotFoundException::class, NoSuchMethodException::class, InstantiationException::class, IllegalAccessException::class, InvocationTargetException::class)
    private fun invokeScalaDoc(args: MutableList<String?>, isScala3: Boolean) {
        val scalaClassLoader = Thread.currentThread().getContextClassLoader()

        val scaladocFqName = if (isScala3) "dotty.tools.scaladoc.Main" else "scala.tools.nsc.ScalaDoc"
        val scaladocEntryName = if (isScala3) "run" else "process"

        val scaladocClass = scalaClassLoader.loadClass(scaladocFqName)
        val process = scaladocClass.getMethod(scaladocEntryName, Array<String>::class.java)
        val scaladoc: Any = scaladocClass.getDeclaredConstructor().newInstance()
        process.invoke(scaladoc, *arrayOf<Any>(args.toTypedArray<String?>()))
    }

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations?
}
