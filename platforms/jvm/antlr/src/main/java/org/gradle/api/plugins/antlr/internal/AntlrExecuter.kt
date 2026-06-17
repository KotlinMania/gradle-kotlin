/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal

import com.google.common.collect.Lists
import org.gradle.api.GradleException
import org.gradle.api.plugins.antlr.internal.antlr2.GenerationPlanBuilder
import org.gradle.api.plugins.antlr.internal.antlr2.MetadataExtractor
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.internal.reflect.JavaMethod
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.process.internal.worker.RequestHandler
import org.gradle.util.internal.RelativePathUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

class AntlrExecuter : RequestHandler<AntlrSpec?, AntlrResult?> {
    override fun run(spec: AntlrSpec): AntlrResult {
        var antlrTool: AntlrTool = Antlr4Tool()
        if (antlrTool.available()) {
            LOGGER.info("Processing with ANTLR 4")
            return antlrTool.process(spec)
        }

        antlrTool = Antlr3Tool()
        if (antlrTool.available()) {
            LOGGER.info("Processing with ANTLR 3")
            return antlrTool.process(spec)
        }

        antlrTool = Antlr2Tool()
        if (antlrTool.available()) {
            LOGGER.info("Processing with ANTLR 2")
            return antlrTool.process(spec)
        }
        throw IllegalStateException("No Antlr implementation available")
    }

    private class Antlr3Tool : AntlrTool() {
        @Throws(ClassNotFoundException::class)
        override fun invoke(arguments: MutableList<String?>, inputDirectory: File?): Int {
            errorIfPackageArgumentSet(arguments, "3")
            val backedObject: Any = loadTool("org.antlr.Tool", null)
            val argArray = arguments.toTypedArray<String?>()
            if (inputDirectory != null) {
                JavaMethod.of(backedObject, Void::class.java, "setInputDirectory", String::class.java).invoke(backedObject, inputDirectory.getAbsolutePath())
                JavaMethod.of(backedObject, Void::class.java, "setForceRelativeOutput", Boolean::class.javaPrimitiveType).invoke(backedObject, true)
            }
            JavaMethod.of(backedObject, Void::class.java, "processArgs", Array<String>::class.java).invoke(backedObject, arrayOf<Any>(argArray))
            JavaMethod.of(backedObject, Void::class.java, "process").invoke(backedObject)
            return JavaMethod.of(backedObject, Int::class.java, "getNumErrors").invoke(backedObject)
        }

        override fun available(): Boolean {
            try {
                loadTool("org.antlr.Tool", null)
            } catch (cnf: ClassNotFoundException) {
                return false
            }
            return true
        }
    }

    private abstract class AntlrTool {
        fun process(spec: AntlrSpec): AntlrResult {
            try {
                return doProcess(spec)
            } catch (e: ClassNotFoundException) {
                //this shouldn't happen if you call check availability with #available first
                throw GradleException("Cannot process antlr sources", e)
            }
        }

        /**
         * process used for antlr3/4
         */
        @Throws(ClassNotFoundException::class)
        open fun doProcess(spec: AntlrSpec): AntlrResult {
            var numErrors = 0
            if (spec.getInputDirectories().size == 0) {
                // we have not root source folder information for the grammar files,
                // so we don't force relativeOutput as we can't calculate it.
                // This results in flat generated sources in the output directory
                numErrors += invoke(spec.asArgumentsWithFiles(), null)
            } else {
                val onWindows = current()!!.isWindows
                for (inputDirectory in spec.getInputDirectories()) {
                    val arguments = spec.getArguments()
                    arguments.add("-o")
                    arguments.add(spec.getOutputDirectory().getAbsolutePath())
                    for (grammarFile in spec.getGrammarFiles()) {
                        var relativeGrammarFilePath = RelativePathUtil.relativePath(inputDirectory, grammarFile)
                        if (onWindows) {
                            relativeGrammarFilePath = relativeGrammarFilePath.replace('/', File.separatorChar)
                        }
                        arguments.add(relativeGrammarFilePath)
                    }
                    numErrors += invoke(arguments, inputDirectory)
                }
            }
            return AntlrResult(numErrors)
        }

        @Throws(ClassNotFoundException::class)
        abstract fun invoke(arguments: MutableList<String?>?, inputDirectory: File?): Int

        abstract fun available(): Boolean

        companion object {
            /**
             * Utility method to create an instance of the Tool class.
             *
             * @throws ClassNotFoundException if class was not on the runtime classpath.
             */
            @Throws(ClassNotFoundException::class)
            fun loadTool(className: String?, args: Array<String?>?): Any {
                try {
                    val toolClass = Class.forName(className) // ok to use caller classloader
                    if (args == null) {
                        return JavaReflectionUtil.newInstance(toolClass)
                    } else {
                        val constructor: Constructor<*> = toolClass.getConstructor(Array<String>::class.java)
                        return constructor.newInstance(*arrayOf<Any>(args))
                    }
                } catch (cnf: ClassNotFoundException) {
                    throw cnf
                } catch (e: InvocationTargetException) {
                    throw GradleException("Failed to load ANTLR", e.cause)
                } catch (e: Exception) {
                    throw GradleException("Failed to load ANTLR", e)
                }
            }

            protected fun toArray(strings: MutableList<String?>): Array<String?> {
                return strings.toTypedArray<String?>()
            }
        }
    }

    internal class Antlr4Tool : AntlrTool() {
        @Throws(ClassNotFoundException::class)
        override fun invoke(arguments: MutableList<String?>, inputDirectory: File?): Int {
            val backedObject: Any = loadTool("org.antlr.v4.Tool", toArray(arguments))
            if (inputDirectory != null) {
                setField(backedObject, "inputDirectory", inputDirectory)
            }
            JavaMethod.of(backedObject, Void::class.java, "processGrammarsOnCommandLine").invoke(backedObject)
            return JavaMethod.of(backedObject, Int::class.java, "getNumErrors").invoke(backedObject)
        }

        override fun available(): Boolean {
            try {
                loadTool("org.antlr.v4.Tool", null)
            } catch (cnf: ClassNotFoundException) {
                return false
            }
            return true
        }

        companion object {
            private fun setField(`object`: Any, fieldName: String, value: File?) {
                try {
                    val field = `object`.javaClass.getField(fieldName)
                    field.set(`object`, value)
                } catch (e: NoSuchFieldException) {
                    throw throwAsUncheckedException(e)
                } catch (e: IllegalAccessException) {
                    throw throwAsUncheckedException(e)
                }
            }
        }
    }

    private class Antlr2Tool : AntlrTool() {
        @Throws(ClassNotFoundException::class)
        override fun doProcess(spec: AntlrSpec): AntlrResult {
            val xref = MetadataExtractor.extractMetadata(spec.getGrammarFiles())
            val generationPlans = GenerationPlanBuilder(spec.getOutputDirectory()).buildGenerationPlans(xref)
            for (generationPlan in generationPlans) {
                val generationPlanArguments: MutableList<String?> = Lists.newArrayList<String?>(spec.getArguments())
                generationPlanArguments.add("-o")
                generationPlanArguments.add(generationPlan.getGenerationDirectory().getAbsolutePath())
                generationPlanArguments.add(generationPlan.getSource().getAbsolutePath())
                try {
                    invoke(generationPlanArguments, null)
                } catch (e: RuntimeException) {
                    if (e.message == "ANTLR Panic: Exiting due to errors.") {
                        return AntlrResult(-1, e)
                    }
                    throw e
                }
            }
            return AntlrResult(0) // ANTLR 2 always returning 0
        }

        /**
         * inputDirectory is not used in antlr2
         */
        @Throws(ClassNotFoundException::class)
        override fun invoke(arguments: MutableList<String?>, inputDirectory: File?): Int {
            errorIfPackageArgumentSet(arguments, "2")
            val backedAntlrTool: Any = loadTool("antlr.Tool", null)
            JavaMethod.of(backedAntlrTool, Int::class.java, "doEverything", Array<String>::class.java).invoke(backedAntlrTool, arrayOf<Any>(toArray(arguments)))
            return 0
        }

        override fun available(): Boolean {
            try {
                loadTool("antlr.Tool", null)
            } catch (cnf: ClassNotFoundException) {
                return false
            }
            return true
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(AntlrExecuter::class.java)

        private fun errorIfPackageArgumentSet(arguments: MutableList<String?>, antlrVersion: String?) {
            require(!arguments.contains(AntlrSpec.Companion.PACKAGE_ARG)) { "The " + AntlrSpec.Companion.PACKAGE_ARG + " argument is not supported by ANTLR " + antlrVersion + "." }
        }
    }
}
