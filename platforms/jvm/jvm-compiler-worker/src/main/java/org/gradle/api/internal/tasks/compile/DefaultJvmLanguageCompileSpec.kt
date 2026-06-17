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

import com.google.common.collect.ImmutableList
import java.io.File
import java.io.Serializable

open class DefaultJvmLanguageCompileSpec : JvmLanguageCompileSpec, Serializable {
    private var workingDir: File? = null
    private var tempDir: File? = null
    private var classpath: MutableList<File?>? = null
    private var destinationDir: File? = null
    private var sourceFiles: Iterable<File?>? = null
    private var release: Int? = null
    private var sourceCompatibility: String? = null
    private var targetCompatibility: String? = null
    private var sourceRoots: MutableList<File?>? = null

    override fun getWorkingDir(): File? {
        return workingDir
    }

    override fun setWorkingDir(workingDir: File?) {
        this.workingDir = workingDir
    }

    override fun getDestinationDir(): File? {
        return destinationDir
    }

    override fun setDestinationDir(destinationDir: File?) {
        this.destinationDir = destinationDir
    }

    override fun getTempDir(): File? {
        return tempDir
    }

    override fun setTempDir(tempDir: File?) {
        this.tempDir = tempDir
    }

    override fun getSourceFiles(): Iterable<File?>? {
        return sourceFiles
    }

    override fun setSourceFiles(sourceFiles: Iterable<File?>?) {
        this.sourceFiles = sourceFiles
    }

    override fun getCompileClasspath(): MutableList<File?> {
        if (classpath == null) {
            classpath = ImmutableList.of<File?>()
        }
        return classpath!!
    }

    override fun setCompileClasspath(classpath: MutableList<File?>?) {
        this.classpath = classpath
    }

    override fun getRelease(): Int? {
        return release
    }

    override fun setRelease(release: Int?) {
        this.release = release
    }

    override fun getSourceCompatibility(): String? {
        return sourceCompatibility
    }

    override fun setSourceCompatibility(sourceCompatibility: String?) {
        this.sourceCompatibility = sourceCompatibility
    }

    override fun getTargetCompatibility(): String? {
        return targetCompatibility
    }

    override fun setTargetCompatibility(targetCompatibility: String?) {
        this.targetCompatibility = targetCompatibility
    }

    override fun getSourceRoots(): MutableList<File?>? {
        return sourceRoots
    }

    override fun setSourcesRoots(sourceRoots: MutableList<File?>?) {
        this.sourceRoots = sourceRoots
    }
}
