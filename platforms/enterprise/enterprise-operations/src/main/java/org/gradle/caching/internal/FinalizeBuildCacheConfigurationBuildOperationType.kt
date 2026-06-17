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
package org.gradle.caching.internal

import org.gradle.internal.operations.BuildOperationType

/**
 * The transformation of the user's build cache config, to the effective configuration.
 *
 * This operation should occur some time after the configuration phase.
 * In practice, it will fire as part of bootstrapping the execution of the first task to execute.
 *
 * This operation should always be executed, regardless of whether caching is enabled/disabled.
 * That is, determining enabled-ness is part of "finalizing".
 * However, if the build fails during configuration or task graph assembly, it will not be emitted.
 * It must fire before any build cache is used.
 *
 * See BuildCacheControllerFactory.
 *
 * @since 4.0
 */
class FinalizeBuildCacheConfigurationBuildOperationType private constructor() :
    BuildOperationType<FinalizeBuildCacheConfigurationBuildOperationType.Details, FinalizeBuildCacheConfigurationBuildOperationType.Result> {
    interface Details {
        /**
         * The path to the build that the build cache configuration is associated with.
         *
         * @since 4.5
         */
        val buildPath: String?
    }

    interface Result {
        val isEnabled: Boolean

        val isLocalEnabled: Boolean

        val isRemoteEnabled: Boolean

        val local: BuildCacheDescription?

        val remote: BuildCacheDescription?

        interface BuildCacheDescription {
            /**
             * The class name of the DSL configuration type.
             *
             * E.g. `org.gradle.caching.local.DirectoryBuildCache`.
             */
            val className: String?

            /**
             * The human friendly description of the type (e.g. "HTTP", "directory")
             *
             * See `org.gradle.caching.BuildCacheServiceFactory.Describer#type(String)`.
             */
            val type: String?

            /**
             * Whether push was enabled.
             */
            val isPush: Boolean

            /**
             * The advertised config parameters of the cache.
             * No null values or keys.
             * Ordered by key lexicographically.
             *
             * See `org.gradle.caching.BuildCacheServiceFactory.Describer#config(String, String)`.
             */
            val config: MutableMap<String?, String?>?
        }
    }
}
