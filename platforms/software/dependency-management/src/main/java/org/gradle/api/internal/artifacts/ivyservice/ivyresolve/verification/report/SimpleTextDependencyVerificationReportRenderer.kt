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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.logging.text.TreeFormatter
import java.nio.file.Path

/**
 * A text report renderer, which is *not* cumulative and is used to report failures in
 * a concise way on the console.
 */
internal class SimpleTextDependencyVerificationReportRenderer(gradleUserHome: Path, documentationRegistry: DocumentationRegistry) :
    AbstractTextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry) {
    private val artifacts: MutableSet<String> = LinkedHashSet<String>()

    private var artifact: ModuleComponentArtifactIdentifier? = null

    override fun startNewSection(title: String) {
        formatter = TreeFormatter()
        formatter.node("Dependency verification failed for " + title)
    }

    override fun startArtifactErrors(action: Runnable) {
        action.run()
    }

    override fun startNewArtifact(key: ModuleComponentArtifactIdentifier, action: Runnable) {
        artifact = key
        action.run()
    }

    override fun reportFailure(failure: RepositoryAwareVerificationFailure) {
        artifacts.add(artifact!!.getDisplayName() + " from repository " + failure.repositoryName)
    }

    override fun finish(highLevelErrors: VerificationHighLevelErrors) {
        val size = artifacts.size
        if (size == 1) {
            formatter.node("One artifact failed verification: " + artifacts.iterator().next())
        } else {
            formatter.node(artifacts.size.toString() + " artifacts failed verification")
            formatter.startChildren()
            for (artifact in artifacts) {
                formatter.node(artifact)
            }
            formatter.endChildren()
        }
        super.finish(highLevelErrors)
    }

    override fun reportAsMultipleErrors(action: Runnable) {
        action.run()
    }
}
