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
package org.gradle.tooling.internal.protocol

import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException

/**
 * Mixed into a provider connection, to allow tooling models to be requested by the client
 * and to run client-provided actions (including builds) with cancellation support.
 *
 *
 * DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 *
 * Consumer compatibility: This interface is used by all consumer versions from 2.1-rc-1.
 *
 * Provider compatibility: This interface is implemented by all provider versions from 2.1-rc-1. Methods have different version compatibilities.
 *
 * @since 2.1-rc-1
 * @see InternalParameterAcceptingConnection
 *
 * @see ConnectionVersion4
 */
interface InternalCancellableConnection : InternalProtocolInterface {
    /**
     * Performs some action against a build and returns the requested model.
     *
     *
     * Consumer compatibility: This method is used by all consumer versions from 2.1-rc-1.
     *
     * Provider compatibility: This method is implemented by all provider versions from 2.1-rc-1.
     *
     * @param modelIdentifier The identifier of the model to build.
     * @param cancellationToken The token to propagate cancellation.
     * @throws BuildExceptionVersion1 On build failure.
     * @throws InternalUnsupportedModelException When the requested model is not supported.
     * @throws InternalUnsupportedBuildArgumentException When the specified command-line options are not supported.
     * @throws InternalBuildCancelledException When the operation was cancelled before it could complete.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 2.1-rc-1
     */
    @Throws(
        BuildExceptionVersion1::class,
        InternalUnsupportedModelException::class,
        InternalUnsupportedBuildArgumentException::class,
        InternalBuildCancelledException::class,
        IllegalStateException::class
    )
    fun getModel(
        modelIdentifier: ModelIdentifier?, cancellationToken: InternalCancellationToken?,
        operationParameters: BuildParameters?
    ): BuildResult<*>?

    /**
     * Performs some action against a build and returns the result.
     *
     *
     * Consumer compatibility: This method is used by all consumer versions from 2.1-rc-1 to 4.3. It is also used by later consumers when the provider does not
     * implement newer interfaces.
     *
     *
     * Provider compatibility: This method is implemented by all provider versions from 2.1-rc-1.
     *
     * @throws BuildExceptionVersion1 On build failure.
     * @throws InternalUnsupportedBuildArgumentException When the specified command-line options are not supported.
     * @throws InternalBuildActionFailureException When the action fails with an exception.
     * @throws InternalBuildCancelledException When the operation was cancelled before it could complete.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 2.1-rc-1
     */
    @Deprecated("4.4. Use {@link InternalParameterAcceptingConnection#run(InternalBuildActionVersion2, InternalCancellationToken, BuildParameters)} instead.")
    @Throws(
        BuildExceptionVersion1::class,
        InternalUnsupportedBuildArgumentException::class,
        InternalBuildActionFailureException::class,
        InternalBuildCancelledException::class,
        IllegalStateException::class
    )
    fun <T> run(
        action: InternalBuildAction<T?>?,
        cancellationToken: InternalCancellationToken?,
        operationParameters: BuildParameters?
    ): BuildResult<T?>?
}
