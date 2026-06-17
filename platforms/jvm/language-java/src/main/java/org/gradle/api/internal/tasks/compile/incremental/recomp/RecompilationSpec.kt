/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import java.util.Collections

class RecompilationSpec {
    private val classesToCompile: MutableSet<String?> = LinkedHashSet<String?>()
    private val sourcePaths: MutableSet<String?> = LinkedHashSet<String?>()
    private val classesToProcess: MutableCollection<String?> = LinkedHashSet<String?>()
    private val resourcesToGenerate: MutableCollection<GeneratedResource?> = LinkedHashSet<GeneratedResource?>()
    var fullRebuildCause: String? = null

    override fun toString(): String {
        return "RecompilationSpec{" +
                "classesToCompile=" + classesToCompile +
                ", classesToProcess=" + classesToProcess +
                ", resourcesToGenerate=" + resourcesToGenerate +
                ", sourcePaths=" + sourcePaths +
                ", fullRebuildCause='" + fullRebuildCause + '\'' +
                ", buildNeeded=" + this.isBuildNeeded +
                ", fullRebuildNeeded=" + this.isFullRebuildNeeded +
                '}'
    }

    fun addClassToCompile(classToCompile: String?): Boolean {
        return classesToCompile.add(classToCompile)
    }

    fun addClassesToCompile(classes: MutableCollection<String?>) {
        classesToCompile.addAll(classes)
    }

    fun getClassesToCompile(): MutableSet<String?> {
        return Collections.unmodifiableSet<String?>(classesToCompile)
    }

    fun addClassToReprocess(classToReprocess: String?) {
        classesToProcess.add(classToReprocess)
    }

    fun getClassesToProcess(): MutableCollection<String?> {
        return Collections.unmodifiableCollection<String?>(classesToProcess)
    }

    fun addResourcesToGenerate(resources: MutableCollection<GeneratedResource?>) {
        resourcesToGenerate.addAll(resources)
    }

    fun getResourcesToGenerate(): MutableCollection<GeneratedResource?> {
        return Collections.unmodifiableCollection<GeneratedResource?>(resourcesToGenerate)
    }

    fun addSourcePath(sourcePath: String?) {
        sourcePaths.add(sourcePath)
    }

    fun addSourcePaths(sourcePath: MutableSet<String?>) {
        sourcePaths.addAll(sourcePath)
    }

    fun getSourcePaths(): MutableCollection<String?> {
        return Collections.unmodifiableCollection<String?>(sourcePaths)
    }

    val isBuildNeeded: Boolean
        get() = this.isFullRebuildNeeded || !classesToCompile.isEmpty() || !classesToProcess.isEmpty()

    val isFullRebuildNeeded: Boolean
        get() = fullRebuildCause != null
}
