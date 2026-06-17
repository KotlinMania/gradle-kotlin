/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.process.internal

import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.file.PathToFileResolver
import org.jspecify.annotations.NullMarked
import java.util.concurrent.Executor

@NullMarked
class DefaultClientExecHandleBuilderFactory private constructor(
    private val fileResolver: PathToFileResolver,
    private val executor: Executor,
    private val buildCancellationToken: BuildCancellationToken
) : ClientExecHandleBuilderFactory {
    override fun newExecHandleBuilder(): ClientExecHandleBuilder {
        return DefaultClientExecHandleBuilder(fileResolver, executor, buildCancellationToken)
    }

    /**
     * An instance of [ClientExecHandleBuilderFactory] that delegates to DefaultClientExecHandleBuilderFactory, but is also Stoppable.
     *
     * This is only used in DefaultDaemonStarter, and it should also stay this way. Ideally we would even remove it at one point.
     */
    class RootClientExecHandleBuilderFactory private constructor(private val delegate: DefaultClientExecHandleBuilderFactory) : ClientExecHandleBuilderFactory, Stoppable {
        override fun newExecHandleBuilder(): ClientExecHandleBuilder {
            return delegate.newExecHandleBuilder()
        }

        override fun stop() {
            CompositeStoppable.stoppable(delegate.executor).stop()
        }

        companion object {
            /**
             * Creates a new [RootClientExecHandleBuilderFactory] for Daemon starter.
             *
             * This instance has unmanaged executor so the caller has to call [.stop] to stop when instance is not needed anymore.
             */
            fun of(
                fileResolver: PathToFileResolver,
                executorFactory: ExecutorFactory,
                buildCancellationToken: BuildCancellationToken
            ): RootClientExecHandleBuilderFactory {
                val clientExecHandleBuilderFactory: DefaultClientExecHandleBuilderFactory = DefaultClientExecHandleBuilderFactory.Companion.of(fileResolver, executorFactory, buildCancellationToken)
                return RootClientExecHandleBuilderFactory(clientExecHandleBuilderFactory)
            }
        }
    }

    companion object {
        fun of(
            fileResolver: PathToFileResolver,
            executorFactory: ExecutorFactory,
            buildCancellationToken: BuildCancellationToken
        ): DefaultClientExecHandleBuilderFactory {
            val executor = executorFactory.create("Exec process")
            return DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken)
        }

        fun of(
            fileResolver: PathToFileResolver,
            executor: Executor,
            buildCancellationToken: BuildCancellationToken
        ): DefaultClientExecHandleBuilderFactory {
            return DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken)
        }
    }
}
