/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.deprecation

import org.gradle.util.GradleVersion

class DeprecationTimeline private constructor(private val messagePattern: String, private val targetVersion: GradleVersion, private val message: String? = null) {
    override fun toString(): String {
        return if (message == null) String.format(messagePattern, targetVersion.getMajorVersion()) else String.format(messagePattern, targetVersion.getMajorVersion(), message)
    }

    companion object {
        fun willBeRemovedInVersion(version: GradleVersion): DeprecationTimeline {
            return DeprecationTimeline("This is scheduled to be removed in Gradle %s.", version)
        }

        fun willBecomeAnErrorInVersion(version: GradleVersion): DeprecationTimeline {
            return DeprecationTimeline("This will fail with an error in Gradle %s.", version)
        }

        fun behaviourWillBeRemovedInVersion(version: GradleVersion): DeprecationTimeline {
            return DeprecationTimeline("This behavior is scheduled to be removed in Gradle %s.", version)
        }

        fun willChangeInVersion(version: GradleVersion): DeprecationTimeline {
            return DeprecationTimeline("This will change in Gradle %s.", version)
        }

        fun startingWithVersion(version: GradleVersion, message: String): DeprecationTimeline {
            return DeprecationTimeline("Starting with Gradle %s, %s.", version, message)
        }
    }
}
