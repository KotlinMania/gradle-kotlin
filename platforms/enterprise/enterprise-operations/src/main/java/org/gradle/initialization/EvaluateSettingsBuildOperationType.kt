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
package org.gradle.initialization

import org.gradle.internal.operations.BuildOperationType

class EvaluateSettingsBuildOperationType private constructor() : BuildOperationType<EvaluateSettingsBuildOperationType.Details, EvaluateSettingsBuildOperationType.Result> {
    interface Details {
        /**
         * @since 4.6
         */
        val buildPath: String?

        /**
         * The absolute path to the settings directory.
         */
        val settingsDir: String?

        /**
         * The absolute path to the settings file.
         */
        val settingsFile: String?
    }

    interface Result
}
