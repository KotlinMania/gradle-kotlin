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

import java.io.File

interface JavaCompileSpec : JvmLanguageCompileSpec {
    @JvmField
    val compileOptions: MinimalJavaCompileOptions?

    override fun getDestinationDir(): File?

    @JvmField
    var classBackupDir: File?

    /**
     * The annotation processor path to use. When empty, no processing should be done. When not empty, processing should be done.
     */
    @JvmField
    var annotationProcessorPath: MutableList<File?>?

    @JvmField
    var effectiveAnnotationProcessors: MutableSet<AnnotationProcessorDeclaration?>?

    /**
     * Classes to process are already compiled classes that are passed to Java compiler.
     * They are passed to Java compiler since they are required by some annotation processor to revisit.
     */
    @JvmField
    var classesToProcess: MutableSet<String?>?

    /**
     * Classes to compile are all classes that we know from Java sources that will be compiled.
     * These classes are deleted before a compilation and are not passed to Java compiler (their sources are passed to a compiler).
     * We only need them in [CompilationClassBackupService] so we know what files don't need a backup.
     */
    var classesToCompile: MutableSet<String?>?

    @JvmField
    var modulePath: MutableList<File?>?

    fun annotationProcessingConfigured(): Boolean {
        return !this.annotationProcessorPath!!.isEmpty() && !this.compileOptions!!.getCompilerArgs().contains("-proc:none")
    }
}
