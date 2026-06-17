/*
 * Copyright 2022 the original author or authors.
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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.internal.DocLinkInternal
import org.gradle.internal.exceptions.Contextual
import javax.annotation.CheckReturnValue

object DocumentedFailure {
    @JvmStatic
    fun builder(): Builder {
        return DocumentedFailure.Builder()
    }

    class Builder private constructor() : Documentation.AbstractBuilder<Builder?>() {
        private var summary: String? = null
        private var advice: String? = null
        private var contextualAdvice: String? = null
        private var documentation: DocLink? = null

        @CheckReturnValue
        fun withSummary(summary: String): Builder {
            this.summary = summary
            return this
        }

        @CheckReturnValue
        fun withContext(contextualAdvice: String): Builder {
            this.contextualAdvice = contextualAdvice
            return this
        }

        @CheckReturnValue
        fun withAdvice(advice: String): Builder {
            this.advice = advice
            return this
        }

        @CheckReturnValue
        override fun withDocumentation(documentation: DocLink): Builder {
            this.documentation = documentation
            return this
        }

        @CheckReturnValue
        fun build(): GradleException {
            return build(null)
        }

        @CheckReturnValue
        fun build(cause: Throwable?): GradleException {
            val outputBuilder = StringBuilder(summary)
            append(outputBuilder, contextualAdvice)
            append(outputBuilder, advice)
            append(outputBuilder, (documentation as DocLinkInternal).consultDocumentationMessage)
            return if (cause == null)
                GradleException(outputBuilder.toString())
            else
                DocumentedExceptionWithCause(outputBuilder.toString(), cause)
        }

        companion object {
            private fun append(outputBuilder: StringBuilder, message: String?) {
                if (!StringUtils.isEmpty(message)) {
                    outputBuilder.append(" ").append(message)
                }
            }
        }
    }

    @Contextual
    class DocumentedExceptionWithCause(message: String, cause: Throwable?) : GradleException(message, cause)
}
