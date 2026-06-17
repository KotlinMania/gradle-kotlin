/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.nativeplatform.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.tasks.util.PatternSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader
import java.io.File

class SourceCompileTaskConfig(languageTransform: NativeLanguageTransform<*>?, taskType: Class<out DefaultTask?>?) : CompileTaskConfig(languageTransform, taskType) {
    protected override fun configureCompileTask(abstractTask: AbstractNativeCompileTask?, binary: NativeBinarySpecInternal, sourceSet: LanguageSourceSetInternal) {
        val task = abstractTask as AbstractNativeSourceCompileTask

        task.setDescription("Compiles the " + sourceSet + " of " + binary)

        task.source(sourceSet.source)

        val project = task.getProject()

        task.objectFileDir.fileProvider(
            project.getLayout().getBuildDirectory().getAsFile().map<S?>(Transformer { it: File? -> File(binary.namingScheme.getOutputDirectory(it, "objs"), sourceSet.projectScopedName) })
        )

        // If this task uses a pre-compiled header
        if (sourceSet is DependentSourceSetInternal && (sourceSet as DependentSourceSetInternal).getPreCompiledHeader() != null) {
            val dependentSourceSet = sourceSet as DependentSourceSetInternal
            val pch: PreCompiledHeader = binary.getPrefixFileToPCH().get(dependentSourceSet.getPrefixHeaderFile())!!
            pch.prefixHeaderFile = dependentSourceSet.getPrefixHeaderFile()
            pch.includeString = dependentSourceSet.getPreCompiledHeader()
            task.preCompiledHeader = pch
        }

        binary.binaryInputs(task.getOutputs().getFiles().getAsFileTree().matching(PatternSet().include("**/*.obj", "**/*.o")))
    }
}
