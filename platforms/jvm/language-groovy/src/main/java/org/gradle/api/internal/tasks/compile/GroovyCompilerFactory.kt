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

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.daemon.ClassloaderIsolatedCompilerWorkerExecutor
import org.gradle.api.internal.tasks.compile.daemon.CompilerWorkerExecutor
import org.gradle.api.internal.tasks.compile.daemon.DaemonGroovyCompiler
import org.gradle.api.internal.tasks.compile.daemon.ProcessIsolatedCompilerWorkerExecutor
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.CompilerFactory
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory
import org.gradle.workers.internal.WorkerDaemonFactory

@ServiceScope(Scope.Project::class)
class GroovyCompilerFactory(
    private val workerDaemonFactory: WorkerDaemonFactory,
    private val inProcessWorkerFactory: IsolatedClassloaderWorkerFactory,
    private val forkOptionsFactory: JavaForkOptionsFactory,
    private val processorDetector: AnnotationProcessorDetector,
    private val jvmVersionDetector: JvmVersionDetector,
    private val workerDirectoryProvider: WorkerDirectoryProvider,
    private val classPathRegistry: ClassPathRegistry,
    private val classLoaderRegistry: ClassLoaderRegistry,
    private val actionExecutionSpecFactory: ActionExecutionSpecFactory,
    private val projectCacheDir: ProjectCacheDir,
    private val problems: ProblemsInternal
) : CompilerFactory<GroovyJavaJointCompileSpec?> {
    override fun newCompiler(spec: GroovyJavaJointCompileSpec): Compiler<GroovyJavaJointCompileSpec> {
        val groovyOptions: MinimalGroovyCompileOptions = spec.groovyCompileOptions!!
        val compilerWorkerExecutor = newExecutor(groovyOptions)
        val groovyCompiler: Compiler<GroovyJavaJointCompileSpec> = DaemonGroovyCompiler(
            workerDirectoryProvider.getWorkingDirectory(),
            classPathRegistry,
            compilerWorkerExecutor,
            classLoaderRegistry,
            forkOptionsFactory,
            jvmVersionDetector,
            problems.internalReporter
        )
        return AnnotationProcessorDiscoveringCompiler<GroovyJavaJointCompileSpec>(NormalizingGroovyCompiler(groovyCompiler), processorDetector)
    }

    private fun newExecutor(groovyOptions: MinimalGroovyCompileOptions): CompilerWorkerExecutor {
        return if (groovyOptions.isFork) ProcessIsolatedCompilerWorkerExecutor(workerDaemonFactory, actionExecutionSpecFactory, projectCacheDir) else ClassloaderIsolatedCompilerWorkerExecutor(
            inProcessWorkerFactory,
            actionExecutionSpecFactory,
            projectCacheDir
        )
    }
}
