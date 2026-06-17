/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.swift

import com.google.common.collect.Iterables
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.gradle.api.Action
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.SafeFileLocationUtils
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.function.Function

// TODO(daniel): Swift compiler should extends from an abstraction of NativeCompiler (most of it applies to SwiftCompiler)
internal class SwiftCompiler(
    buildOperationExecutor: BuildOperationExecutor,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    commandLineToolInvocationWorker: CommandLineToolInvocationWorker?,
    invocationContext: CommandLineToolContext,
    private val objectFileExtension: String?,
    workerLeaseService: WorkerLeaseService,
    private val swiftCompilerVersion: VersionNumber
) : AbstractCompiler<SwiftCompileSpec?>(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, SwiftCompileArgsTransformer(), false, workerLeaseService) {
    private val swiftDepsHandler: SwiftDepsHandler

    init {
        this.swiftDepsHandler = SwiftDepsHandler()
    }

    override fun addOptionsFileArgs(args: MutableList<String?>?, tempDir: File?) {
    }

    override fun execute(spec: SwiftCompileSpec): WorkResult? {
        require(
            !(swiftCompilerVersion.getMajor() < spec.sourceCompatibility.getVersion() || (swiftCompilerVersion.getMajor() >= 5 && spec.sourceCompatibility == SwiftVersion.SWIFT3))
        ) { String.format("Swift compiler version '%s' doesn't support Swift language version '%d'", swiftCompilerVersion.toString(), spec.sourceCompatibility.getVersion()) }
        return super.execute(spec)
    }

    protected fun getOutputFileDir(sourceFile: File, objectFileDir: File?, fileSuffix: String?): File {
        val windowsPathLimitation = OperatingSystem.current()!!.isWindows

        val outputFile = compilerOutputFileNamingSchemeFactory.create()
            .withObjectFileNameSuffix(fileSuffix)
            .withOutputBaseFolder(objectFileDir)
            .map(sourceFile)
        val outputDirectory = outputFile.getParentFile()
        GFileUtils.mkdirs(outputDirectory)
        return if (windowsPathLimitation) SafeFileLocationUtils.assertInWindowsPathLengthLimitation(outputFile) else outputFile
    }

    override fun newInvocationAction(spec: SwiftCompileSpec, genericArgs: MutableList<String?>): Action<BuildOperationQueue<CommandLineToolInvocation?>?>? {
        val objectDir = spec.objectFileDir
        return object : Action<BuildOperationQueue<CommandLineToolInvocation?>?> {
            override fun execute(buildQueue: BuildOperationQueue<CommandLineToolInvocation?>) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation())

                val outputFileMap = OutputFileMap()

                val moduleSwiftDeps = File(objectDir, "module.swiftdeps")
                outputFileMap.root().swiftDependenciesFile(moduleSwiftDeps)

                for (sourceFile in spec.sourceFiles!!) {
                    outputFileMap.newEntry(sourceFile!!.getAbsolutePath())
                        .dependencyFile(getOutputFileDir(sourceFile, objectDir, ".d"))
                        .diagnosticsFile(getOutputFileDir(sourceFile, objectDir, ".dia"))
                        .objectFile(getOutputFileDir(sourceFile, objectDir, objectFileExtension))
                        .swiftModuleFile(getOutputFileDir(sourceFile, objectDir, "~partial.swiftmodule"))
                        .swiftDependenciesFile(getOutputFileDir(sourceFile, objectDir, ".swiftdeps"))
                    genericArgs.add(sourceFile.getAbsolutePath())
                }
                if (null != spec.moduleName) {
                    genericArgs.add("-module-name")
                    genericArgs.add(spec.moduleName)
                    genericArgs.add("-emit-module-path")
                    genericArgs.add(spec.moduleFile.getAbsolutePath())
                }


                val canSafelyCompileIncrementally = swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, spec.changedFiles)
                if (canSafelyCompileIncrementally) {
                    genericArgs.add("-incremental")
                    genericArgs.add("-emit-dependencies")
                }

                genericArgs.add("-emit-object")

                val outputFileMapFile = File(spec.objectFileDir, "output-file-map.json")
                outputFileMap.writeToFile(outputFileMapFile)

                val outputArgs: MutableList<String?> = ArrayList<String?>()
                outputArgs.add("-output-file-map")
                outputArgs.add(outputFileMapFile.getAbsolutePath())

                val importRootArgs: MutableList<String?> = ArrayList<String?>()
                for (importRoot in spec.includeRoots!!) {
                    importRootArgs.add("-I")
                    importRootArgs.add(importRoot!!.getAbsolutePath())
                }
                if (spec.isDebuggable) {
                    genericArgs.add("-g")
                }
                if (spec.isOptimized) {
                    genericArgs.add("-O")
                }

                genericArgs.addAll(CollectionUtils.< String, String > collect<String?, String?>(spec.macros.keySet(), Function { macro: String? -> "-D" + macro }))

                genericArgs.add("-swift-version")
                genericArgs.add(spec.sourceCompatibility.getVersion().toString())

                val perFileInvocation =
                    newInvocation("compiling swift file(s)", objectDir, Iterables.concat<String?>(genericArgs, outputArgs, importRootArgs), spec.getOperationLogger())
                perFileInvocation!!.environment!!.put("TMPDIR", spec.getTempDir().getAbsolutePath())
                buildQueue.add(perFileInvocation)
            }
        }
    }

    private class SwiftCompileArgsTransformer : ArgsTransformer<SwiftCompileSpec?> {
        override fun transform(swiftCompileSpec: SwiftCompileSpec): MutableList<String?>? {
            return swiftCompileSpec.getArgs()
        }
    }

    internal class OutputFileMap {
        private val entries: MutableMap<String?, Entry?> = HashMap<String?, Entry?>()

        fun root(): Builder {
            return newEntry("")
        }

        fun newEntry(name: String?): Builder {
            val entry = Entry()
            entries.put(name, entry)

            return Builder(entry)
        }

        private fun toJson(writer: Appendable?) {
            val gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
                .setPrettyPrinting()
                .create()
            gson.toJson(entries, writer)
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        fun writeToFile(outputFile: File) {
            try {
                PrintWriter(outputFile).use { writer ->
                    toJson(writer)
                }
            } catch (ex: IOException) {
                throw throwAsUncheckedException(ex)
            }
        }

        private class Builder(private val entry: Entry) {
            fun dependencyFile(dependencyFile: File): Builder {
                entry.dependencies = dependencyFile.getAbsolutePath()
                return this
            }

            fun objectFile(objectFile: File): Builder {
                entry.`object` = objectFile.getAbsolutePath()
                return this
            }

            fun swiftModuleFile(swiftModuleFile: File): Builder {
                entry.swiftmodule = swiftModuleFile.getAbsolutePath()
                return this
            }

            fun swiftDependenciesFile(swiftDependenciesFile: File): Builder {
                entry.swiftDependencies = swiftDependenciesFile.getAbsolutePath()
                return this
            }

            fun diagnosticsFile(diagnosticsFile: File): Builder {
                entry.diagnostics = diagnosticsFile.getAbsolutePath()
                return this
            }
        }

        @Suppress("unused") // Used by Gson
        private class Entry {
            private var dependencies: String? = null
            private var `object`: String? = null
            private var swiftmodule: String? = null
            private var swiftDependencies: String? = null
            private var diagnostics: String? = null
        }
    }
}
