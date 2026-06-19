/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.jvm

import org.gradle.api.file.FileCollection
import java.io.File

interface JavaModuleDetector {
    fun inferClasspath(inferModulePath: Boolean, classpath: MutableCollection<File?>): FileCollection

    fun inferClasspath(inferModulePath: Boolean, classpath: FileCollection?): FileCollection

    fun inferModulePath(inferModulePath: Boolean, classpath: MutableCollection<File?>): FileCollection

    fun inferModulePath(inferModulePath: Boolean, classpath: FileCollection?): FileCollection

    fun isModule(inferModulePath: Boolean, files: FileCollection): Boolean

    fun isModule(inferModulePath: Boolean, file: File): Boolean

    companion object {
        @JvmStatic
        fun isModuleSource(inferModulePath: Boolean, sourceRoots: Iterable<File?>): Boolean {
            return inferModulePath && sourceRoots.any { File(it, "module-info.java").isFile }
        }
    }
}
