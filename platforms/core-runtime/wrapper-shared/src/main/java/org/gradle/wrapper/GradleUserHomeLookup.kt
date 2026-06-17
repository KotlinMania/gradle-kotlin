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
package org.gradle.wrapper

import java.io.File

object GradleUserHomeLookup {
    val DEFAULT_GRADLE_USER_HOME: String = System.getProperty("user.home") + "/.gradle"
    const val GRADLE_USER_HOME_PROPERTY_KEY: String = "gradle.user.home"
    const val GRADLE_USER_HOME_ENV_KEY: String = "GRADLE_USER_HOME"

    @JvmStatic
    fun gradleUserHome(): File {
        var gradleUserHome: String?
        if ((System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY).also { gradleUserHome = it }) != null) {
            return File(gradleUserHome)
        }
        if ((System.getenv(GRADLE_USER_HOME_ENV_KEY).also { gradleUserHome = it }) != null) {
            return File(gradleUserHome)
        }
        return File(DEFAULT_GRADLE_USER_HOME)
    }
}
