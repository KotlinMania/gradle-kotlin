/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.DaemonForkOptions
import org.gradle.workers.internal.DefaultWorkResult
import org.gradle.workers.internal.IsolatedClassLoaderWorkerRequirement
import org.gradle.workers.internal.WorkerFactory

/**
 * Base implementation of [CompilerWorkerExecutor] which handles submitting a compile work item to execute.
 * Inheritors need to provide an appropriate isolated worker requirement depending on what isolation mode is being used.
 */
abstract class AbstractIsolatedCompilerWorkerExecutor(private val workerFactory: WorkerFactory, private val actionExecutionSpecFactory: ActionExecutionSpecFactory) : CompilerWorkerExecutor {
    abstract fun getIsolatedWorkerRequirement(daemonForkOptions: DaemonForkOptions?): IsolatedClassLoaderWorkerRequirement?

    override fun execute(parameters: CompilerParameters?, daemonForkOptions: DaemonForkOptions?, additionalAllowedClasses: MutableSet<Class<*>?>?): DefaultWorkResult? {
        val workerRequirement = getIsolatedWorkerRequirement(daemonForkOptions)
        val worker = workerFactory.getWorker(workerRequirement)

        return worker.execute(
            actionExecutionSpecFactory.newIsolatedSpec<CompilerParameters?>(
                "compiler daemon",
                CompilerWorkAction::class.java,
                parameters,
                workerRequirement,
                additionalAllowedClasses
            )
        )
    }
}
