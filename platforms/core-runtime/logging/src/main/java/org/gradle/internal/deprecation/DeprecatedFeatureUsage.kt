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

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.apache.commons.lang3.StringUtils
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.internal.DeprecationData
import org.gradle.api.problems.internal.DocLinkInternal
import org.gradle.internal.featurelifecycle.FeatureUsage

class DeprecatedFeatureUsage : FeatureUsage {
    /**
     * When the feature will be removed, and how if relevant.
     *
     * Example: This feature will be removed in Gradle 10.
     */
    @JvmField
    val removalDetails: String

    /**
     * General, non usage specific, advice on what to do about this notice.
     *
     * Example: Use method Foo.baz() instead.
     */
    @JvmField
    val advice: String?

    /**
     * Advice on what to do about the notice, specific to this usage.
     *
     * Example: Annotation processors Foo, Bar and Baz were found on the compile classpath.
     */
    @JvmField
    val contextualAdvice: String?

    /**
     * Link to documentation, describing how to migrate from this deprecated usage.
     *
     * Example: https://docs.gradle.org/current/userguide/upgrading_version_5.html#plugin_validation_changes
     */
    val documentationUrl: DocLink?
    @JvmField
    val problemIdDisplayName: String
    private val problemId: String

    @JvmField
    val type: Type

    constructor(
        summary: String,
        removalDetails: String,
        advice: String?,
        contextualAdvice: String?,
        documentation: DocLink?,
        type: Type,
        problemIdDisplayName: String,
        problemId: String,
        calledFrom: Class<*>
    ) : super(summary, calledFrom) {
        this.removalDetails = Preconditions.checkNotNull<String>(removalDetails)
        this.advice = advice
        this.contextualAdvice = contextualAdvice
        this.type = Preconditions.checkNotNull<Type>(type)
        this.documentationUrl = documentation
        this.problemIdDisplayName = problemIdDisplayName
        this.problemId = problemId
    }

    @VisibleForTesting
    internal constructor(usage: DeprecatedFeatureUsage) : super(usage.summary, usage.calledFrom) {
        this.removalDetails = usage.removalDetails
        this.advice = usage.advice
        this.contextualAdvice = usage.contextualAdvice
        this.documentationUrl = usage.documentationUrl
        this.type = usage.type
        this.problemIdDisplayName = usage.problemIdDisplayName
        this.problemId = usage.problemId
    }

    fun getProblemId(): String? {
        return problemId
    }

    /**
     * Indicates the type of usage, affecting the feedback that can be given.
     */
    enum class Type {
        /**
         * The key characteristic is that the trace to the usage indicates the offending user code.
         *
         * Example: calling a deprecated method.
         */
        USER_CODE_DIRECT,

        /**
         * The key characteristic is that the trace to the usage DOES NOT indicate the offending user code,
         * but the usage happens during runtime and may be associated to a logical entity (e.g. task, plugin).
         *
         * The association between a usage and entity is not modelled by the usage,
         * but can be inferred from the operation stream (for deprecations, for which operation progress events are emitted).
         *
         * Example: annotation processor on compile classpath (feature is used at compile, not classpath definition)
         */
        USER_CODE_INDIRECT,

        /**
         * The key characteristic is that there is no useful "where was it used information",
         * as the usage relates to how/where Gradle was invoked.
         *
         * Example: deprecated CLI switch.
         */
        BUILD_INVOCATION;

        fun toDeprecationDataType(): DeprecationData.Type {
            when (this) {
                Type.USER_CODE_DIRECT -> return DeprecationData.Type.USER_CODE_DIRECT
                Type.USER_CODE_INDIRECT -> return DeprecationData.Type.USER_CODE_INDIRECT
                Type.BUILD_INVOCATION -> return DeprecationData.Type.BUILD_INVOCATION
            }
            throw IllegalStateException("Unknown deprecation type: " + this)
        }
    }

    override fun formattedMessage(): String {
        val outputBuilder = StringBuilder(summary)
        append(outputBuilder, removalDetails)
        append(outputBuilder, contextualAdvice)
        append(outputBuilder, advice)
        if (this.documentationUrl != null) {
            append(outputBuilder, (this.documentationUrl as DocLinkInternal).getConsultDocumentationMessage())
        }
        return outputBuilder.toString()
    }

    companion object {
        private fun append(outputBuilder: StringBuilder, message: String?) {
            if (StringUtils.isNotEmpty(message)) {
                outputBuilder.append(" ").append(message)
            }
        }
    }
}
