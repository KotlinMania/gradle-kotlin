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

import org.gradle.api.problems.DocLink

internal class DeprecationMessage(
    private val summary: String,
    private val removalDetails: String,
    private val advice: String?,
    private val context: String?,
    private val documentation: DocLink?,
    private val usageType: DeprecatedFeatureUsage.Type,
    private val problemIdDisplayName: String,
    private val problemId: String
) {
    fun toDeprecatedFeatureUsage(calledFrom: Class<*>): DeprecatedFeatureUsage {
        return DeprecatedFeatureUsage(summary, removalDetails, advice, context, documentation, usageType, problemIdDisplayName, problemId, calledFrom)
    }
}
