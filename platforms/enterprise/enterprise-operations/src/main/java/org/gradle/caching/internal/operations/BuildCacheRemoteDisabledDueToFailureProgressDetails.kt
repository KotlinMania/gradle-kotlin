/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.caching.internal.operations

/**
 * Marks that the remote build cache has been disabled due to a failure.
 *
 *
 * There might be multiple disabled events from build cache operations happening in parallel.
 *
 * @since 8.6
 */
interface BuildCacheRemoteDisabledDueToFailureProgressDetails {
    /**
     * The identifier of the configuration of the remote build cache.
     *
     *
     * For Gradle, this is the `buildPath` of the build that configured the remote build cache in use.
     *
     * @since 8.6
     */
    val buildCacheConfigurationIdentifier: String?

    /**
     * The cache key.
     *
     * @since 8.6
     */
    val cacheKey: String?

    /**
     * The failure that caused the build cache to be disabled.
     *
     * @since 8.6
     */
    val failure: Throwable?

    /**
     * The type of operation that had the failure.
     *
     * @since 8.6
     */
    val operationType: BuildCacheOperationType?

    /**
     * The type of build cache operation.
     *
     * @since 8.6
     */
    enum class BuildCacheOperationType {
        /**
         * @since 8.6
         */
        LOAD,

        /**
         * @since 8.6
         */
        STORE
    }
}
