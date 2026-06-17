/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.daemon.ProcessIsolatedCompilerWorkerExecutor
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory

class DefaultJavaCompilerFactory(
    private val workingDirProvider: WorkerDirectoryProvider,
    private val workerDaemonFactory: WorkerDaemonFactory?,
    private val forkOptionsFactory: JavaForkOptionsFactory?,
    private val execHandleFactory: ClientExecHandleBuilderFactory?,
    private val processorDetector: AnnotationProcessorDetector?,
    private val classPathRegistry: ClassPathRegistry,
    private val actionExecutionSpecFactory: ActionExecutionSpecFactory?,
    private val problems: ProblemsInternal,
    private val projectCacheDir: ProjectCacheDir?
) : JavaCompilerFactory {
    private var javaHomeBasedJavaCompilerFactory: JavaHomeBasedJavaCompilerFactory? = null
        get() {
            if (field == null) {
                field = JavaHomeBasedJavaCompilerFactory(classPathRegistry.getClassPath("JAVA-COMPILER-PLUGIN").getAsFiles())
            }
            return field
        }

    override fun <T : CompileSpec?> create(type: Class<T?>): Compiler<T?> {
        val result = createTargetCompiler<T?>(type)
        return ModuleApplicationNameWritingCompiler<JavaCompileSpec?>(
            AnnotationProcessorDiscoveringCompiler<JavaCompileSpec?>(
                NormalizingJavaCompiler(result as Compiler<JavaCompileSpec?>),
                processorDetector
            )
        ) as Compiler<T?>
    }

    private fun <T : CompileSpec?> createTargetCompiler(type: Class<T?>): Compiler<T?> {
        require(JavaCompileSpec::class.java.isAssignableFrom(type)) { String.format("Cannot create a compiler for a spec with type %s", type.getSimpleName()) }

        if (CommandLineJavaCompileSpec::class.java.isAssignableFrom(type)) {
            return CommandLineJavaCompiler(execHandleFactory) as Compiler<T?>
        }

        if (ForkingJavaCompileSpec::class.java.isAssignableFrom(type)) {
            return DaemonJavaCompiler(
                workingDirProvider.getWorkingDirectory(),
                this.javaHomeBasedJavaCompilerFactory, ProcessIsolatedCompilerWorkerExecutor(workerDaemonFactory, actionExecutionSpecFactory, projectCacheDir), forkOptionsFactory, classPathRegistry
            ) as Compiler<T?>
        } else {
            return JdkJavaCompiler(this.javaHomeBasedJavaCompilerFactory!!, problems) as Compiler<T?>
        }
    }
}
