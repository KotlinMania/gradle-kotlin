/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.initialization.layout.ProjectCacheDir
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory
import org.gradle.workers.internal.WorkerDaemonFactory

class GroovyServices : AbstractGradleModuleServices() {
    public override fun registerProjectServices(registration: ServiceRegistration) {
        registration.addProvider(ProjectServices())
    }

    private class ProjectServices : ServiceRegistrationProvider {
        @Provides
        fun createGroovyCompilerFactory(
            workerDaemonFactory: WorkerDaemonFactory,
            inProcessWorkerFactory: IsolatedClassloaderWorkerFactory,
            forkOptionsFactory: JavaForkOptionsFactory,
            processorDetector: AnnotationProcessorDetector,
            jvmVersionDetector: JvmVersionDetector,
            workerDirectoryProvider: WorkerDirectoryProvider,
            classPathRegistry: ClassPathRegistry,
            classLoaderRegistry: ClassLoaderRegistry,
            actionExecutionSpecFactory: ActionExecutionSpecFactory,
            projectCacheDir: ProjectCacheDir,
            problems: ProblemsInternal
        ): GroovyCompilerFactory {
            return GroovyCompilerFactory(
                workerDaemonFactory,
                inProcessWorkerFactory,
                forkOptionsFactory,
                processorDetector,
                jvmVersionDetector,
                workerDirectoryProvider,
                classPathRegistry,
                classLoaderRegistry,
                actionExecutionSpecFactory,
                projectCacheDir,
                problems
            )
        }
    }
}
