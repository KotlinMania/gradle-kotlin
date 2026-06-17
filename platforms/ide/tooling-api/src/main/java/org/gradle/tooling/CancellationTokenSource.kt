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
package org.gradle.tooling

/**
 * A `CancellationTokenSource` allows you to issue cancellation requests to one or more [LongRunningOperation]
 * instances. To use a token source:
 *
 *
 *  * Create a token source using [GradleConnector.newCancellationTokenSource].
 *  * Attach the token to one or more operations using [LongRunningOperation.withCancellationToken].
 * You need to do this before you start the operation.
 *
 *  * Later, you can cancel the associated operations by calling [.cancel] on this token source.
 *
 *
 *
 * All implementations of this interface are required to be thread safe.
 *
 * @since 2.1
 */
interface CancellationTokenSource {
    /**
     * Initiates cancel request. All operations that have been associated with this token will be cancelled.
     *
     *
     * It is assumed that the implementation will do 'best-effort' attempt to perform cancellation.
     * This method returns immediately and if the cancellation is successful the cancelled operation
     * will notify its [ResultHandler.onFailure]
     * with a [BuildCancelledException] describing how it was cancelled.
     *
     */
    fun cancel()

    /**
     * Returns a token associated with this `CancellationTokenSource`.
     * Always returns the same instance.
     *
     * @return The cancellation token.
     */
    fun token(): CancellationToken?
}
