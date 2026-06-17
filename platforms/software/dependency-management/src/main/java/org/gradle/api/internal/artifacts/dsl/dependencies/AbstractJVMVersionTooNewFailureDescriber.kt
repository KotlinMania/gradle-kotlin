/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.Companion.toVersion
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import java.util.Objects
import java.util.Optional
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * Abstract base class for building [ResolutionFailureDescriber]s that describe [ResolutionFailure]s caused by
 * incompatibilities related to a dependency requiring a higher JVM version.
 */
abstract class AbstractJVMVersionTooNewFailureDescriber : AbstractResolutionFailureDescriber<NoCompatibleVariantsFailure?>() {
    /**
     * Returns the JVM version used in the comparison against the library that is causing the failure.
     *
     * @param failure the failure
     * @return the JVM version that is incompatible
     */
    protected abstract fun getJVMVersion(failure: NoCompatibleVariantsFailure): JavaVersion?

    protected fun isDueToJVMVersionTooNew(failure: NoCompatibleVariantsFailure): Boolean {
        if (allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(failure)) {
            val minJVMVersionSupported = findMinJVMSupported(failure.candidates).orElseThrow<java.lang.IllegalStateException>(Supplier { IllegalStateException() })
            return minJVMVersionSupported.compareTo(getJVMVersion(failure)!!) > 0
        } else {
            return false
        }
    }

    private fun allLibraryCandidatesIncompatibleDueToJVMVersionTooLow(failure: NoCompatibleVariantsFailure): Boolean {
        val libraryCandidates = failure.candidates.stream()
            .filter { candidate: ResolutionCandidateAssessor.AssessedCandidate? -> this.isLibraryCandidate(candidate!!) }
            .collect(Collectors.toList())
        if (!libraryCandidates.isEmpty()) {
            val requestingJDKVersion = failure.getRequestedAttributes().contains(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
            val allIncompatibleDueToJDKVersion =
                libraryCandidates.stream().allMatch { candidate: ResolutionCandidateAssessor.AssessedCandidate? -> this.isJVMVersionAttributeIncompatible(candidate!!) }
            return requestingJDKVersion && allIncompatibleDueToJDKVersion
        } else {
            return false
        }
    }

    protected fun findMinJVMSupported(candidates: MutableList<ResolutionCandidateAssessor.AssessedCandidate>): Optional<JavaVersion> {
        return candidates.stream()
            .filter { candidate: ResolutionCandidateAssessor.AssessedCandidate? -> this.isLibraryCandidate(candidate!!) }
            .map<Optional<JavaVersion>> { candidate: ResolutionCandidateAssessor.AssessedCandidate? -> this.findMinJVMSupported(candidate!!) }
            .filter { obj: Optional<JavaVersion?>? -> obj!!.isPresent() }
            .map<JavaVersion> { obj: Optional<JavaVersion?>? -> obj!!.get() }
            .min(Comparator.comparing<JavaVersion, String>(JavaVersion::majorVersion))
    }

    private fun findMinJVMSupported(candidate: ResolutionCandidateAssessor.AssessedCandidate): Optional<JavaVersion> {
        return candidate.incompatibleAttributes.stream()
            .filter({ attribute: ResolutionCandidateAssessor.AssessedAttribute<*> -> this.isJVMVersionAttribute(attribute) })
            .map({ jvmVersionAttribute -> toVersion(Objects.requireNonNull<Any>(jvmVersionAttribute.provided)) })
            .min(Comparator.comparing<T, U>(JavaVersion::majorVersion))
    }

    private fun isLibraryCandidate(candidate: ResolutionCandidateAssessor.AssessedCandidate): Boolean {
        val candidateAttributes: AttributeContainer = candidate.allCandidateAttributes.getAttributes()
        for (attribute in candidateAttributes.keySet()) {
            if (attribute.getName() == Category.CATEGORY_ATTRIBUTE.getName()) {
                val category: String? = candidateAttributes.getAttribute(attribute).toString()
                return category == Category.LIBRARY
            }
        }
        return false
    }

    private fun isJVMVersionAttributeIncompatible(candidate: ResolutionCandidateAssessor.AssessedCandidate): Boolean {
        return candidate.incompatibleAttributes.stream().anyMatch({ attribute: ResolutionCandidateAssessor.AssessedAttribute<*> -> this.isJVMVersionAttribute(attribute) })
    }

    private fun isJVMVersionAttribute(attribute: ResolutionCandidateAssessor.AssessedAttribute<*>): Boolean {
        return attribute.attribute.getName() == TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.getName()
    }
}
