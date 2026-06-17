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
package org.gradle.api.tasks.wrapper.internal

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.wrapper.Install
import org.jspecify.annotations.NullMarked

@NullMarked
object WrapperDefaults {
    const val SCRIPT_PATH: String = "gradlew"
    const val JAR_FILE_PATH: String = "gradle/wrapper/gradle-wrapper.jar"
    val DISTRIBUTION_TYPE: Wrapper.DistributionType = Wrapper.DistributionType.BIN

    val DISTRIBUTION_PATH: String = Install.DEFAULT_DISTRIBUTION_PATH
    val DISTRIBUTION_BASE: Wrapper.PathBase = Wrapper.PathBase.GRADLE_USER_HOME
    val ARCHIVE_PATH: String = DISTRIBUTION_PATH
    val ARCHIVE_BASE: Wrapper.PathBase = DISTRIBUTION_BASE

    const val NETWORK_TIMEOUT: Int = 10000
    const val VALIDATE_DISTRIBUTION_URL: Boolean = true

    val RETRIES: Int = Install.DEFAULT_NETWORK_RETRIES
    val RETRY_BACK_OFF_MS: Int = Install.DEFAULT_NETWORK_RETRY_BACK_OFF_MS
}
