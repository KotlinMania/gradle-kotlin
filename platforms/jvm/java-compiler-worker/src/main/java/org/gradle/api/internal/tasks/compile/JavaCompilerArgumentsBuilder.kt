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

import com.google.common.base.Joiner
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.util.internal.GUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.stream.Collectors

class JavaCompilerArgumentsBuilder(private val spec: JavaCompileSpec) {
    private var includeLauncherOptions = false
    private var includeMainOptions = true
    private var includeClasspath = true
    private var includeSourceFiles = false
    private var allowEmptySourcePath = true

    private var args: MutableList<String?>? = null

    fun includeLauncherOptions(flag: Boolean): JavaCompilerArgumentsBuilder {
        includeLauncherOptions = flag
        return this
    }

    fun includeMainOptions(flag: Boolean): JavaCompilerArgumentsBuilder {
        includeMainOptions = flag
        return this
    }

    fun includeClasspath(flag: Boolean): JavaCompilerArgumentsBuilder {
        includeClasspath = flag
        return this
    }

    fun includeSourceFiles(flag: Boolean): JavaCompilerArgumentsBuilder {
        includeSourceFiles = flag
        return this
    }

    fun noEmptySourcePath(): JavaCompilerArgumentsBuilder {
        allowEmptySourcePath = false
        return this
    }

    /**
     * Returns a list with all Java compiler arguments as configured in this builder.
     * Returned arguments are guaranteed not to be null.
     *
     * @return a list containing all Java compiler arguments
     */
    fun build(): MutableList<String?> {
        args = ArrayList<String?>()
        // Take a deep copy of the compilerArgs because the following methods mutate it.
        val compilerArgs = uncheckedCast<MutableList<Any?>?>(spec.getCompileOptions().getCompilerArgs())
        val compArgs = compilerArgs!!
            .stream()
            .map<String?> { obj: Any? -> obj.toString() }
            .collect(Collectors.toList())

        validateCompilerArgs(compArgs)

        addLauncherOptions()
        addMainOptions(compArgs)
        addClasspath()
        addUserProvidedArgs(compArgs)
        addSourceFiles()

        return args!!
    }

    private fun validateCompilerArgs(compilerArgs: MutableList<String?>) {
        for (arg in compilerArgs) {
            if ("-sourcepath" == arg || "--source-path" == arg) {
                throw InvalidUserDataException(
                    "Cannot specify -sourcepath or --source-path via `CompileOptions.compilerArgs`. " +
                            "Use the `CompileOptions.sourcepath` property instead."
                )
            }

            if ("-processorpath" == arg || "--processor-path" == arg) {
                throw InvalidUserDataException(
                    "Cannot specify -processorpath or --processor-path via `CompileOptions.compilerArgs`. " +
                            "Use the `CompileOptions.annotationProcessorPath` property instead."
                )
            }

            if (arg != null && arg.startsWith("-J")) {
                throw InvalidUserDataException(
                    "Cannot specify -J flags via `CompileOptions.compilerArgs`. " +
                            "Use the `CompileOptions.forkOptions.jvmArgs` property instead."
                )
            }

            if ("--release" == arg && spec.release != null) {
                throw InvalidUserDataException("Cannot specify --release via `CompileOptions.compilerArgs` when using `CompileOptions.release`.")
            }
        }
    }

    private fun addLauncherOptions() {
        if (!includeLauncherOptions) {
            return
        }

        val forkOptions = spec.getCompileOptions().getForkOptions()
        if (forkOptions.getMemoryInitialSize() != null) {
            args!!.add("-J-Xms" + forkOptions.getMemoryInitialSize().trim { it <= ' ' })
        }
        if (forkOptions.getMemoryMaximumSize() != null) {
            args!!.add("-J-Xmx" + forkOptions.getMemoryMaximumSize().trim { it <= ' ' })
        }
        if (forkOptions.getJvmArgs() != null) {
            args!!.addAll(forkOptions.getJvmArgs()!!)
        }
    }

    private fun addMainOptions(compilerArgs: MutableList<String?>) {
        if (!includeMainOptions) {
            return
        }
        val release = spec.release
        val compileOptions = spec.getCompileOptions()

        if (release != null) {
            args!!.add("--release")
            args!!.add(release.toString())
        } else if (!releaseOptionIsSet(compilerArgs)) {
            val sourceCompatibility = spec.sourceCompatibility
            if (sourceCompatibility != null) {
                args!!.add("-source")
                args!!.add(sourceCompatibility)
            }
            val targetCompatibility = spec.targetCompatibility
            if (targetCompatibility != null) {
                args!!.add("-target")
                args!!.add(targetCompatibility)
            }
        }
        val destinationDir = spec.getDestinationDir()
        if (destinationDir != null) {
            args!!.add("-d")
            args!!.add(destinationDir.getPath())
        }
        if (compileOptions.isVerbose()) {
            args!!.add("-verbose")
        }
        if (compileOptions.isDeprecation()) {
            args!!.add("-deprecation")
        }
        if (!compileOptions.isWarnings()) {
            args!!.add("-nowarn")
        }
        if (compileOptions.getEncoding() != null) {
            args!!.add("-encoding")
            args!!.add(compileOptions.getEncoding())
        }
        val bootClasspath = compileOptions.getBootClasspath()
        if (bootClasspath != null) { //TODO: move bootclasspath to platform
            args!!.add("-bootclasspath")
            args!!.add(bootClasspath)
        }
        if (compileOptions.getExtensionDirs() != null) {
            args!!.add("-extdirs")
            args!!.add(compileOptions.getExtensionDirs())
        }
        if (compileOptions.getHeaderOutputDirectory() != null) {
            args!!.add("-h")
            args!!.add(compileOptions.getHeaderOutputDirectory()!!.getPath())
        }

        if (compileOptions.isDebug()) {
            if (compileOptions.getDebugLevel() != null) {
                args!!.add("-g:" + compileOptions.getDebugLevel().trim { it <= ' ' })
            } else {
                args!!.add("-g")
            }
        } else {
            args!!.add("-g:none")
        }

        addSourcePathArg(compilerArgs, compileOptions)

        if (spec.sourceCompatibility == null || toVersion(spec.sourceCompatibility)!!.compareTo(JavaVersion.VERSION_1_6) >= 0) {
            val annotationProcessorPath = spec.getAnnotationProcessorPath()
            if (annotationProcessorPath == null || annotationProcessorPath.isEmpty()) {
                args!!.add("-proc:none")
            } else {
                args!!.add("-processorpath")
                args!!.add(Joiner.on(File.pathSeparator).join(annotationProcessorPath))
            }
            if (compileOptions.getAnnotationProcessorGeneratedSourcesDirectory() != null) {
                args!!.add("-s")
                args!!.add(compileOptions.getAnnotationProcessorGeneratedSourcesDirectory()!!.getPath())
            }
        }

        /*This is an internal option, it's used in com.sun.tools.javac.util.Names#createTable(Options options). The -XD backdoor switch is used to set it, as described in a comment
        in com.sun.tools.javac.main.RecognizedOptions#getAll(OptionHelper helper). This option was introduced in JDK 7 and controls if compiler's name tables should be reused.
        Without this option being set they are stored in a static list using soft references which can lead to memory pressure and performance deterioration
        when using the daemon, especially when using small heap and building a large project.
        Due to a bug (https://builds.gradle.org/viewLog.html?buildId=284033&tab=buildResultsDiv&buildTypeId=Gradle_Master_Performance_PerformanceExperimentsLinux) no instances of
        SharedNameTable are actually ever reused. It has been fixed for JDK9 and we should consider not using this option with JDK9 as not using it  will quite probably improve the
        performance of compilation.
        Using this option leads to significant performance improvements when using daemon and compiling java sources with JDK7 and JDK8.*/
        args!!.add(USE_UNSHARED_COMPILER_TABLE_OPTION)
    }

    private fun addSourcePathArg(compilerArgs: MutableList<String?>, compileOptions: MinimalJavaCompileOptions) {
        val sourcepath: MutableCollection<File?>? = compileOptions.getSourcepath()
        val emptySourcePath = sourcepath == null || sourcepath.isEmpty()

        if (compilerArgs.contains("--module-source-path")) {
            if (!emptySourcePath) {
                LOGGER.warn("You specified both --module-source-path and a sourcepath. These options are mutually exclusive. Ignoring sourcepath.")
            }
            return
        }

        if (emptySourcePath) {
            if (allowEmptySourcePath) {
                args!!.add("-sourcepath")
                args!!.add("")
            }
            return
        }

        args!!.add("-sourcepath")
        args!!.add(GUtil.asPath(sourcepath))
    }

    private fun addUserProvidedArgs(compilerArgs: MutableList<String?>?) {
        if (!includeMainOptions) {
            return
        }
        if (compilerArgs != null) {
            args!!.addAll(compilerArgs)
        }
    }

    private fun releaseOptionIsSet(compilerArgs: MutableList<String?>?): Boolean {
        return compilerArgs != null && compilerArgs.contains("--release")
    }

    private fun addClasspath() {
        if (!includeClasspath) {
            return
        }

        val classpath = spec.compileClasspath
        val modulePath = spec.getModulePath()
        val moduleVersion = spec.getCompileOptions().getJavaModuleVersion()

        // Even if `classpath` is empty, we still need to pass `-classpath ""` to the compiler.
        // Otherwise, the compiler will try to infer the classpath by looking at the `java.class.path` system property.
        args!!.add("-classpath")
        args!!.add(Joiner.on(File.pathSeparatorChar).join(classpath))

        if (moduleVersion != null) {
            args!!.add("--module-version")
            args!!.add(moduleVersion)
        }

        if (!modulePath.isEmpty()) {
            args!!.add("--module-path")
            args!!.add(Joiner.on(File.pathSeparatorChar).join(modulePath))
        }
    }

    private fun addSourceFiles() {
        if (!includeSourceFiles) {
            return
        }

        for (file in spec.getSourceFiles()) {
            args!!.add(file.getPath())
        }
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(JavaCompilerArgumentsBuilder::class.java)
        const val USE_UNSHARED_COMPILER_TABLE_OPTION: String = "-XDuseUnsharedTable=true"
        const val EMPTY_SOURCE_PATH_REF_DIR: String = "emptySourcePathRef"
    }
}
