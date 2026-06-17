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
package org.gradle.internal.nativeintegration

/**
 * Encapsulates what happened when we tried to modify the environment.
 */
enum class EnvironmentModificationResult(reason: String) {
    SUCCESS(null),
    UNSUPPORTED_ENVIRONMENT("There is no native integration with this operating environment.");

    private val reason: String?

    init {
        this.reason = reason
    }

    override fun toString(): String {
        return reason!!
    }

    val isSuccess: Boolean
        get() = this == EnvironmentModificationResult.SUCCESS
}
