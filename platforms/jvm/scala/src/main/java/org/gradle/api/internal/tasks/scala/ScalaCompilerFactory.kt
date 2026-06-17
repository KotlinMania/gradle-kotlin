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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.daemon.CompilerWorkerExecutor
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerFactory
import org.gradle.process.internal.JavaForkOptionsFactory
import java.io.File

class ScalaCompilerFactory(
    private val daemonWorkingDir: File?, private val compilerWorkerExecutor: CompilerWorkerExecutor?, private val scalaClasspath: FileCollection,
    private val zincClasspath: FileCollection, private val forkOptionsFactory: JavaForkOptionsFactory?,
    private val classPathRegistry: ClassPathRegistry?, private val classLoaderRegistry: ClassLoaderRegistry?,
    private val classpathHasher: ClasspathHasher
) : CompilerFactory<ScalaJavaJointCompileSpec?> {
    override fun newCompiler(spec: ScalaJavaJointCompileSpec?): Compiler<ScalaJavaJointCompileSpec?> {
        val scalaClasspathFiles: MutableSet<File?> = scalaClasspath.getFiles()
        val zincClasspathFiles: MutableSet<File?> = zincClasspath.getFiles()

        val hashedScalaClasspath = HashedClasspath(DefaultClassPath.of(scalaClasspathFiles), classpathHasher)

        // currently, we leave it to ZincScalaCompiler to also compile the Java code
        val scalaCompiler: Compiler<ScalaJavaJointCompileSpec?> = DaemonScalaCompiler<ScalaJavaJointCompileSpec?>(
            daemonWorkingDir,
            hashedScalaClasspath,
            compilerWorkerExecutor,
            zincClasspathFiles,
            forkOptionsFactory,
            classPathRegistry,
            classLoaderRegistry
        )

        return NormalizingScalaCompiler(scalaCompiler)
    }
}
