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
package org.gradle.api.problems.internal

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Objects
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.ProblemDefinition
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.jspecify.annotations.NullMarked
import java.io.Serializable

@NullMarked
class DefaultProblemDefinition @VisibleForTesting internal constructor(
    private val id: ProblemId,
    private val severity: Severity,
    private val documentationLink: DocLink?
) : Serializable, ProblemDefinition {
    override fun getId(): ProblemId {
        return id
    }

    override fun getSeverity(): Severity {
        return severity
    }

    override fun getDocumentationLink(): DocLink? {
        return documentationLink
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultProblemDefinition
        return severity == that.severity &&
                Objects.equal(id, that.id) &&
                Objects.equal(documentationLink, that.documentationLink)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(id, severity, documentationLink)
    }

    override fun toString(): String {
        return "DefaultProblemDefinition{" +
                "id=" + id +
                ", severity=" + severity +
                ", documentationLink=" + documentationLink +
                '}'
    }
}
