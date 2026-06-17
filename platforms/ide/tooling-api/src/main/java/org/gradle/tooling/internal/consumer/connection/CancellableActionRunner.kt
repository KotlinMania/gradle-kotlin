/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.BuildParameters
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.InternalCancellationToken

internal open class CancellableActionRunner(
    private val executor: InternalCancellableConnection,
    private val exceptionTransformer: CancellationExceptionTransformer,
    private val versionDetails: VersionDetails
) : ActionRunner {
    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: BuildAction<T?>, operationParameters: ConsumerOperationParameters): T? {
        val rootDir = operationParameters.getProjectDir()
        val result: BuildResult<T?>?
        try {
            try {
                result = execute<T?>(InternalBuildActionAdapter<T?>(action, rootDir, versionDetails), BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters)
            } catch (e: RuntimeException) {
                throw exceptionTransformer.transform(e)
            }
        } catch (e: InternalBuildActionFailureException) {
            throw BuildActionFailureException("The supplied build action failed with an exception.", e.cause)
        }
        return result.model
    }

    @Suppress("deprecation")
    protected open fun <T> execute(buildActionAdapter: InternalBuildActionAdapter<T?>, cancellationTokenAdapter: InternalCancellationToken, operationParameters: BuildParameters): BuildResult<T?> {
        return executor.run<T?>(buildActionAdapter, cancellationTokenAdapter, operationParameters)
    }
}
