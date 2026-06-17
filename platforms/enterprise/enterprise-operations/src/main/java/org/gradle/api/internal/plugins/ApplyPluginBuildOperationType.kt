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
package org.gradle.api.internal.plugins

import org.gradle.internal.operations.BuildOperationType

/**
 * Details about a plugin being applied.
 *
 * @since 4.0
 */
class ApplyPluginBuildOperationType private constructor() : BuildOperationType<ApplyPluginBuildOperationType.Details?, ApplyPluginBuildOperationType.Result?> {
    interface Details {
        /**
         * The fully qualified plugin ID, if known.
         */
        val pluginId: String?

        /**
         * The class of the plugin implementation.
         */
        val pluginClass: Class<*>?

        /**
         * The target of the plugin.
         * One of "gradle", "settings", "project".
         */
        val targetType: String?

        /**
         * If the target is a project, its path.
         */
        val targetPath: String?

        /**
         * The build path of the target.
         */
        val buildPath: String?

        /**
         * A unique ID for this plugin application, within this build operation tree.
         *
         * @see org.gradle.configuration.internal.ExecuteListenerBuildOperationType.Details.getApplicationId
         * @since 4.10
         */
        val applicationId: Long
    }

    interface Result
}
