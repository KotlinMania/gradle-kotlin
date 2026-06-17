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
package org.gradle.language.cpp.internal.tooling

import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask
import java.io.File
import java.io.Serializable

class DefaultCompilationDetails(
    val compileTask: LaunchableGradleTask?,
    val compilerExecutable: File?,
    val compileWorkingDir: File?,
    val sources: MutableList<DefaultSourceFile?>?,
    val headerDirs: MutableList<File?>?,
    val systemHeaderSearchPaths: MutableList<File?>?,
    val userHeaderSearchPaths: MutableList<File?>?,
    val macroDefines: MutableList<DefaultMacroDirective?>?,
    val additionalArgs: MutableList<String?>?
) : Serializable {
    val frameworkSearchPaths: MutableList<File?>
        get() = mutableListOf<File?>()

    val macroUndefines: MutableList<String?>
        get() = mutableListOf<String?>()
}
