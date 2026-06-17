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
 * A text report renderer, which is *not* cumulative.
 */
internal class TextDependencyVerificationReportRenderer(gradleUserHome: Path, documentationRegistry: DocumentationRegistry) :
    AbstractTextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry) {
    private var inMultiErrors = false

    override fun startNewSection(title: String) {
        formatter = TreeFormatter()
        formatter.node("Dependency verification failed for " + title)
    }

    override fun startArtifactErrors(action: Runnable) {
        formatter.startChildren()
        action.run()
        formatter.endChildren()
        formatter.blankLine()
    }

    override fun startNewArtifact(key: ModuleComponentArtifactIdentifier, action: Runnable) {
        formatter.node("On artifact " + key.getDisplayName() + " ")
        action.run()
    }

    override fun reportFailure(failure: RepositoryAwareVerificationFailure) {
        if (inMultiErrors) {
            formatter.node("")
        }
        formatter.append("in repository '" + failure.repositoryName + "': ")
        failure.failure.explainTo(formatter)
    }

    override fun reportAsMultipleErrors(action: Runnable) {
        formatter.append("multiple problems reported:")
        formatter.startChildren()
        inMultiErrors = true
        action.run()
        inMultiErrors = false
        formatter.endChildren()
    }

    private fun processAffectedFiles(affectedFiles: MutableSet<String>) {
        formatter.blankLine()
        formatter.node("These files failed verification:")
        formatter.startChildren()
        for (affectedFile in affectedFiles) {
            formatter.node(affectedFile)
        }
        formatter.endChildren()
        formatter.blankLine()
        formatter.node("GRADLE_USER_HOME = " + gradleUserHome)
    }

    override fun finish(highLevelErrors: VerificationHighLevelErrors) {
        super.finish(highLevelErrors)
        val affectedFiles = highLevelErrors.getAffectedFiles()
        if (!affectedFiles.isEmpty()) {
            processAffectedFiles(affectedFiles)
        }
        formatter.blankLine()
        formatter.node("These files failed verification:")
        formatter.startChildren()
        for (affectedFile in highLevelErrors.getAffectedFiles()) {
            formatter.node(affectedFile)
        }
        formatter.endChildren()
        formatter.blankLine()
        formatter.node("GRADLE_USER_HOME = " + gradleUserHome)
    }
}
