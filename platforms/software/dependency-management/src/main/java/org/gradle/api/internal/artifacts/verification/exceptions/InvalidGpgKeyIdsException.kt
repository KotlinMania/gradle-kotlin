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
package org.gradle.api.internal.artifacts.verification.exceptions

import org.gradle.api.GradleException
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.text.TreeFormatter

/**
 * Exception class used when a GPG IDs were not correct.
 *
 *
 *
 * An example is using short/long IDs instead of fingerprints when trusting keys
 */
class InvalidGpgKeyIdsException
/**
 * Creates a new exception with a list of incorrect keys.
 *
 * @param wrongKeys the list of incorrect IDs, which will be nicely formatted as part of the exception messages so the user can find them
 */(private val wrongKeys: MutableList<String?>) : GradleException() {
    /**
     * Formats a nice error message by using a [TreeFormatter].
     *
     *
     *
     * Idea for this method is that you can pass a higher-level [TreeFormatter] into here, and get a coherent, nice error message printed out - so the user will see a nice chain of causes.
     */
    fun formatMessage(formatter: TreeFormatter) {
        val documentLink = DocumentationRegistry()
            .getDocumentationRecommendationFor("on this", "dependency_verification", "sec:understanding-signature-verification")

        formatter.node(String.format("The following trusted GPG IDs are not in a minimum 160-bit fingerprint format (%s):", documentLink))
        formatter.startChildren()
        wrongKeys
            .stream()
            .map<String?> { key: String? -> String.format("'%s'", key) }
            .forEach { text: String? -> formatter.node(text) }
        formatter.endChildren()
    }

    val message: String
        get() {
            val treeFormatter = TreeFormatter()
            formatMessage(treeFormatter)
            return treeFormatter.toString()
        }
}
