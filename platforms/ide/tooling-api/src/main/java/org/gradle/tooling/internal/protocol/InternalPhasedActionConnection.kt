/*
 * Copyright 2018 the original author or authors.
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
 * Mixed into a provider connection, to allow tooling models in different phases of the build to be
 * requested by the user.
 *
 *
 * DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 *
 * Consumer compatibility: This interface is used by all consumer versions from 4.8.
 *
 * Provider compatibility: This interface is implemented by all provider versions from 4.8.
 *
 * @since 4.8
 * @see ConnectionVersion4
 */
interface InternalPhasedActionConnection : InternalProtocolInterface {
    /**
     * Performs some action against a build and returns the result.
     *
     *
     * Consumer compatibility: This method is used by all consumer versions from 4.8.
     *
     * Provider compatibility: This method is implemented by all provider versions from 4.8.
     *
     * @return The result of the entire build. A `Void` type is expected as result. Results for individual actions
     * are supplied to their respective handlers.
     *
     * @throws BuildExceptionVersion1 On build failure.
     * @throws InternalUnsupportedBuildArgumentException When the specified command-line options are not supported.
     * @throws InternalBuildActionFailureException When one of the actions fails with an exception.
     * @throws InternalBuildCancelledException When the operation was cancelled before it could complete.
     * @throws IllegalStateException When this connection has been stopped.
     */
    @Throws(
        BuildExceptionVersion1::class,
        InternalUnsupportedBuildArgumentException::class,
        InternalBuildActionFailureException::class,
        InternalBuildCancelledException::class,
        IllegalStateException::class
    )
    fun run(
        internalPhasedAction: InternalPhasedAction?,
        listener: PhasedActionResultListener?,
        cancellationToken: InternalCancellationToken?,
        operationParameters: BuildParameters?
    ): BuildResult<*>?
}
