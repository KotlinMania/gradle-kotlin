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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.logging.text.TreeFormatter
import java.nio.file.Path

internal abstract class AbstractTextDependencyVerificationReportRenderer(protected val gradleUserHome: Path, protected val documentationRegistry: DocumentationRegistry) :
    DependencyVerificationReportRenderer {
    protected var formatter: TreeFormatter? = null

    protected fun legend(legendItem: String) {
        formatter!!.node(legendItem)
    }

    override fun finish(highLevelErrors: VerificationHighLevelErrors) {
        if (highLevelErrors.isMaybeCompromised()) {
            val sb = StringBuilder()
            sb.append("This can indicate that a dependency has been compromised. Please carefully verify the ")
            if (highLevelErrors.hasFailedSignatures()) {
                sb.append("signatures and ")
            }
            sb.append("checksums.")
            if (highLevelErrors.hasFailedSignatures() && highLevelErrors.isKeyServersDisabled()) {
                sb.append(" Key servers are disabled, this can indicate that you need to update the local keyring with the missing keys.")
            }
            legend(sb.toString())
        }
        if (highLevelErrors.canSuggestWriteMetadata()) {
            // the else is just to avoid telling people to use `--write-verification-metadata` if we suspect compromised dependencies
            legend(
                "If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. " + documentationRegistry.getDocumentationRecommendationFor(
                    "on how to do this",
                    "dependency_verification",
                    "sec:troubleshooting-verification"
                )
            )
        }
    }

    fun render(): String {
        return formatter.toString()
    }
}
