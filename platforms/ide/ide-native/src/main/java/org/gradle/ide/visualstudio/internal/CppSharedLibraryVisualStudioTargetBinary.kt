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

import org.gradle.api.file.ProjectLayout
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.CppSharedLibrary
import java.io.File

class CppSharedLibraryVisualStudioTargetBinary(projectName: String?, projectPath: String?, component: CppComponent?, private val binary: CppSharedLibrary, projectLayout: ProjectLayout?) :
    AbstractCppBinaryVisualStudioTargetBinary(projectName, projectPath, component, projectLayout) {
    override fun getBinary(): CppBinary {
        return binary
    }

    override fun getProjectType(): VisualStudioTargetBinary.ProjectType {
        return VisualStudioTargetBinary.ProjectType.DLL
    }

    override fun isExecutable(): Boolean {
        return false
    }

    override fun getBuildTaskPath(): String {
        return binary.linkTask.get().getPath()
    }

    override fun getOutputFile(): File {
        return binary.runtimeFile.get().getAsFile()
    }
}
