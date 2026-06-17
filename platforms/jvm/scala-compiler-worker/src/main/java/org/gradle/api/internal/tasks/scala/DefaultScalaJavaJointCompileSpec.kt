/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import java.io.File

class DefaultScalaJavaJointCompileSpec(private val javaExecutable: File?) : DefaultJavaCompileSpec(), ScalaJavaJointCompileSpec {
    private var options: MinimalScalaCompileOptions? = null
    var scalaClasspath: Iterable<File?>? = null
    var zincClasspath: Iterable<File?>? = null
    private var scalaCompilerPlugins: Iterable<File?>? = null
    private var analysisMap: MutableMap<File?, File?>? = null
    private var analysisFile: File? = null
    private var classfileBackupDir: File? = null
    private var buildStartTimestamp: Long = 0

    override fun getJavaExecutable(): File? {
        return javaExecutable
    }

    override fun getScalaCompileOptions(): MinimalScalaCompileOptions? {
        return options
    }

    override fun getAnalysisFile(): File? {
        return analysisFile
    }

    override fun setAnalysisFile(analysisFile: File?) {
        this.analysisFile = analysisFile
    }

    fun setScalaCompileOptions(options: MinimalScalaCompileOptions?) {
        this.options = options
    }

    override fun getClassfileBackupDir(): File? {
        return classfileBackupDir
    }

    override fun setClassfileBackupDir(classfileBackupDir: File?) {
        this.classfileBackupDir = classfileBackupDir
    }

    override fun getScalaCompilerPlugins(): Iterable<File?>? {
        return scalaCompilerPlugins
    }

    override fun setScalaCompilerPlugins(scalaCompilerPlugins: Iterable<File?>?) {
        this.scalaCompilerPlugins = scalaCompilerPlugins
    }

    override fun getAnalysisMap(): MutableMap<File?, File?>? {
        return analysisMap
    }

    override fun setAnalysisMap(analysisMap: MutableMap<File?, File?>?) {
        this.analysisMap = analysisMap
    }

    override fun getBuildStartTimestamp(): Long {
        return buildStartTimestamp
    }

    fun setBuildStartTimestamp(buildStartTimestamp: Long) {
        this.buildStartTimestamp = buildStartTimestamp
    }
}
