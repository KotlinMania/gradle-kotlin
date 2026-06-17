/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.featurelifecycle

import com.google.common.annotations.VisibleForTesting
import org.gradle.internal.SystemProperties
import org.gradle.internal.deprecation.DeprecatedFeatureUsage
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization
import org.gradle.problems.ProblemDiagnostics
import java.util.Collections

class DefaultDeprecatedUsageProgressDetails(@field:VisibleForTesting val featureUsage: DeprecatedFeatureUsage, private val diagnostics: ProblemDiagnostics) : DeprecatedUsageProgressDetails,
    CustomOperationTraceSerialization {
    override fun getSummary(): String {
        return featureUsage.getSummary()
    }

    override fun getRemovalDetails(): String {
        return featureUsage.removalDetails
    }

    override fun getAdvice(): String {
        return featureUsage.advice!!
    }

    override fun getContextualAdvice(): String {
        return featureUsage.contextualAdvice!!
    }

    override fun getDocumentationUrl(): String {
        val documentationUrl = featureUsage.documentationUrl
        return (if (documentationUrl == null) null else documentationUrl.url)!!
    }

    override fun getType(): String {
        return featureUsage.type.name
    }

    override fun getStackTrace(): MutableList<StackTraceElement> {
        return diagnostics.stack
    }

    override fun getCustomOperationTraceSerializableModel(): Any {
        val deprecation: MutableMap<String, Any> = LinkedHashMap<String, Any>()
        deprecation.put("summary", getSummary())
        deprecation.put("removalDetails", getRemovalDetails())
        deprecation.put("advice", getAdvice())
        deprecation.put("contextualAdvice", getContextualAdvice())
        deprecation.put("documentationUrl", getDocumentationUrl())
        deprecation.put("type", getType())
        val sb = StringBuilder()
        for (ste in getStackTrace()) {
            sb.append(ste.toString())
            sb.append(SystemProperties.getInstance().getLineSeparator())
        }
        deprecation.put("stackTrace", sb.toString())
        // the properties are wrapped to an enclosing map to improve the readability of the trace files
        return Collections.singletonMap<String, MutableMap<String, Any>>("deprecation", deprecation)
    }
}
