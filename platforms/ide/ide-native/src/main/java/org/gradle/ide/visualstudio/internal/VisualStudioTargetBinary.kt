/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.ide.visualstudio.internal

import java.util.function.Function

/**
 * A model-agnostic adapter for binary information
 */
interface VisualStudioTargetBinary {
    /**
     * Returns the Gradle project path for this binary
     */
    val projectPath: String?

    /**
     * Returns the name of the Gradle component associated with this binary
     */
    val componentName: String?

    /**
     * Returns the visual studio project name associated with this binary
     */
    val visualStudioProjectName: String?

    /**
     * Returns the visual studio project configuration name associated with this binary
     */
    val visualStudioConfigurationName: String?

    /**
     * Returns the target Visual Studio version of this binary.
     */
    val visualStudioVersion: VersionNumber?

    /**
     * Returns the target SDK version of this binary.
     */
    val sdkVersion: VersionNumber?

    /**
     * Returns the project suffix to use when naming Visual Studio projects
     */
    val projectType: ProjectType?

    /**
     * Returns the variant dimensions associated with this binary
     */
    val variantDimensions: MutableList<String?>?

    /**
     * Returns the source files associated with this binary
     */
    val sourceFiles: FileCollection?

    /**
     * Returns the resource files associated with this binary
     */
    val resourceFiles: FileCollection?

    /**
     * Returns the header files associated with this binary
     */
    val headerFiles: FileCollection?

    /**
     * Returns whether or not this binary represents an executable
     */
    val isExecutable: Boolean

    /**
     * Returns a task that can be used to build this binary
     */
    val buildTaskPath: String?

    /**
     * Returns a task that can be used to clean the outputs of this binary
     */
    val cleanTaskPath: String?

    /**
     * Returns whether or not this binary is a debuggable variant
     */
    val isDebuggable: Boolean

    /**
     * Returns the main product of this binary (i.e. executable or library file)
     */
    val outputFile: File?

    /**
     * Returns the compiler definitions that should be used with this binary
     */
    val compilerDefines: MutableList<String?>?

    /**
     * Returns the language standard of the source for this binary.
     */
    val languageStandard: LanguageStandard?

    /**
     * Returns the include paths that should be used with this binary
     */
    val includePaths: MutableSet<File?>?

    enum class ProjectType(suffix: String) {
        EXE("Exe"), LIB("Lib"), DLL("Dll"), NONE("");

        @JvmField
        val suffix: String?

        init {
            this.suffix = suffix
        }
    }

    enum class LanguageStandard(value: String) {
        NONE(""),
        STD_CPP_14("stdcpp14"),
        STD_CPP_17("stdcpp17"),
        STD_CPP_LATEST("stdcpplatest");

        val value: String?

        init {
            this.value = value
        }

        companion object {
            fun from(arguments: MutableList<String?>): LanguageStandard {
                return arguments.stream().filter { it: String? -> it!!.matches("^[-/]std:c\\+\\+.+".toRegex()) }.findFirst().map<LanguageStandard>(Function { it: String? ->
                    if (it!!.endsWith("++14")) {
                        return@map LanguageStandard.STD_CPP_14
                    } else if (it.endsWith("++17")) {
                        return@map LanguageStandard.STD_CPP_17
                    } else if (it.endsWith("++latest")) {
                        return@map LanguageStandard.STD_CPP_LATEST
                    }
                    LanguageStandard.NONE
                }).orElse(LanguageStandard.NONE)
            }
        }
    }
}
