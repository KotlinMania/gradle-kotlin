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

import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.describer.AbstractResolutionFailureDescriber
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.util.GradleVersion
import java.lang.String
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier
import kotlin.Boolean
import kotlin.Comparator
import kotlin.IllegalStateException
import kotlin.collections.MutableList
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.collections.min
import kotlin.map
import kotlin.sequences.filter
import kotlin.sequences.map
import kotlin.sequences.min
import kotlin.text.format
import kotlin.text.map
import kotlin.text.min

/**
 * A [ResolutionFailureDescriber] that describes a [ResolutionFailure] caused by a plugin requiring
 * a newer Gradle version than the one currently running the build.
 *
 * This is determined by assessing the incompatibility of the [GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE] attribute.
 */
abstract class NewerGradleNeededByPluginFailureDescriber : AbstractResolutionFailureDescriber<NoCompatibleVariantsFailure?>() {
    private val currentGradleVersion: GradleVersion = GradleVersion.current()

    override fun canDescribeFailure(failure: NoCompatibleVariantsFailure): Boolean {
        return !failure.candidates.isEmpty() && allCandidatesIncompatibleDueToGradleVersionTooLow(failure)
    }

    override fun describeFailure(failure: NoCompatibleVariantsFailure): AbstractResolutionFailureException {
        val minGradleApiVersionSupportedByPlugin = findMinGradleVersionSupportedByPlugin(failure.candidates)
        val message = buildPluginNeedsNewerGradleVersionFailureMsg(failure.describeRequestTarget(), minGradleApiVersionSupportedByPlugin)
        val resolutions = buildResolutions(suggestUpdateGradle(minGradleApiVersionSupportedByPlugin), suggestDowngradePlugin(failure.describeRequestTarget()))
        return VariantSelectionByAttributesException(message, failure, resolutions)
    }

    private fun allCandidatesIncompatibleDueToGradleVersionTooLow(failure: NoCompatibleVariantsFailure): Boolean {
        val requestingPluginApi = failure.getRequestedAttributes().contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE)
        val allIncompatibleDueToGradleVersion = failure.candidates.stream()
            .allMatch { candidate: ResolutionCandidateAssessor.AssessedCandidate? ->
                candidate.incompatibleAttributes.stream()
                    .anyMatch({ attribute: ResolutionCandidateAssessor.AssessedAttribute<*> -> this.isGradlePluginApiAttribute(attribute) })
            }
        return requestingPluginApi && allIncompatibleDueToGradleVersion
    }

    private fun findMinGradleVersionSupportedByPlugin(candidates: MutableList<ResolutionCandidateAssessor.AssessedCandidate>): GradleVersion {
        return candidates.stream()
            .map<Optional<GradleVersion>> { candidate: ResolutionCandidateAssessor.AssessedCandidate? -> this.findMinGradleVersionSupportedByPlugin(candidate!!) }
            .filter { obj: Optional<GradleVersion?>? -> obj!!.isPresent() }
            .map<GradleVersion> { obj: Optional<GradleVersion?>? -> obj!!.get() }
            .min(Comparator.comparing<GradleVersion, String>(Function { obj: GradleVersion -> obj.getVersion() }))
            .orElseThrow<java.lang.IllegalStateException>(Supplier { IllegalStateException() })
    }

    private fun findMinGradleVersionSupportedByPlugin(candidate: ResolutionCandidateAssessor.AssessedCandidate): Optional<GradleVersion> {
        return candidate.incompatibleAttributes.stream()
            .filter({ attribute: ResolutionCandidateAssessor.AssessedAttribute<*> -> this.isGradlePluginApiAttribute(attribute) })
            .map({ apiVersionAttribute -> GradleVersion.version(String.valueOf(apiVersionAttribute.provided)) })
            .min(Comparator.comparing<T, U>(Function { obj: T -> obj.getVersion() }))
    }

    private fun isGradlePluginApiAttribute(attribute: ResolutionCandidateAssessor.AssessedAttribute<*>): Boolean {
        return attribute.attribute.getName() == GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE.getName()
    }

    private fun buildPluginNeedsNewerGradleVersionFailureMsg(pluginId: kotlin.String, minRequiredGradleVersion: GradleVersion): kotlin.String {
        return kotlin.String.format(GRADLE_VERSION_TOO_OLD_TEMPLATE, pluginId, minRequiredGradleVersion.getVersion(), currentGradleVersion)
    }

    private fun suggestUpdateGradle(minRequiredGradleVersion: GradleVersion): kotlin.String {
        return "Upgrade to at least Gradle " + minRequiredGradleVersion.getVersion() + ". See the instructions at " + getDocumentationRegistry()!!.getDocumentationFor(
            "upgrading_version_8",
            NEEDS_NEWER_GRADLE_SECTION + "."
        )
    }

    private fun suggestDowngradePlugin(pluginId: kotlin.String): kotlin.String {
        return "Downgrade plugin " + pluginId + " to an older version compatible with " + currentGradleVersion + "."
    }

    companion object {
        private const val GRADLE_VERSION_TOO_OLD_TEMPLATE = "Plugin %s requires at least Gradle %s. This build uses %s."
        private const val NEEDS_NEWER_GRADLE_SECTION = "sub:updating-gradle"
    }
}
