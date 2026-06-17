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
package org.gradle.api.publish.internal.metadata

import com.google.common.base.Objects
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.publish.internal.validation.PublicationErrorChecker
import org.gradle.internal.logging.text.TreeFormatter
import java.util.function.Consumer
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class InvalidPublicationChecker(private val publicationName: String?, private val taskPath: String?, private val suppressedValidationErrors: MutableSet<String?>) {
    private val variants: BiMap<String?, VariantIdentity?> = HashBiMap.create<String?, VariantIdentity?>()
    private val errors: MutableList<String> = ArrayList<String>()
    private val explanations: MutableSet<String> = LinkedHashSet<String>()
    private var publicationHasVersion = false
    private var publicationHasDependencyOrConstraint = false

    fun checkComponent(component: SoftwareComponent?) {
        if (component is SoftwareComponentInternal) {
            PublicationErrorChecker.Companion.checkForUnpublishableAttributes(component, DOCUMENTATION_REGISTRY)
        }
    }

    fun registerVariant(name: String?, attributes: AttributeContainer, capabilities: MutableSet<out Capability?>?) {
        if (attributes.isEmpty()) {
            failWith("Variant '" + name + "' must declare at least one attribute.")
        }
        if (variants.containsKey(name)) {
            failWith("It is invalid to have multiple variants with the same name ('" + name + "')")
        } else {
            val identity = VariantIdentity(attributes, capabilities)
            if (variants.containsValue(identity)) {
                val found = variants.inverse().get(identity)
                failWith("Variants '" + found + "' and '" + name + "' have the same attributes and capabilities. Please make sure either attributes or capabilities are different.")
            } else {
                variants.put(name, identity)
            }
        }
    }

    private fun checkVariantDependencyVersions() {
        if (!suppressedValidationErrors.contains(DEPENDENCIES_WITHOUT_VERSION_SUPPRESSION) && publicationHasDependencyOrConstraint && !publicationHasVersion) {
            // Previous variant did not declare any version
            failWith(
                "Publication only contains dependencies and/or constraints without a version. " +
                        "You should add minimal version information, publish resolved versions (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor(
                    "on this",
                    "publishing_maven",
                    "publishing_maven:resolved_dependencies"
                ) + ") or " +
                        "reference a platform (" + DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("platforms", "platforms") + "). " +
                        "Disable this check by adding 'dependencies-without-versions' to the suppressed validations of the " + taskPath + " task."
            )
        }
    }

    fun validateAttributes(variant: String?, group: String?, name: String?, attributes: AttributeContainer?) {
        for (validator in dependencyAttributeValidators()) {
            val error = validator.validationErrorFor(group, name, attributes)
            error.ifPresent(Consumer { s: String? -> addDependencyValidationError(variant, s, validator.getExplanation(), validator.getSuppressor()) })
        }
    }

    fun validate() {
        if (variants.isEmpty()) {
            failWith("This publication must publish at least one variant")
        }
        checkVariantDependencyVersions()
        if (!errors.isEmpty()) {
            val formatter = TreeFormatter()
            formatter.node("Invalid publication '" + publicationName + "'")
            formatter.startChildren()
            for (error in errors) {
                formatter.node(error)
            }
            formatter.endChildren()
            for (explanation in explanations) {
                formatter.node(explanation)
            }
            throw InvalidUserCodeException(formatter.toString())
        }
    }

    private fun dependencyAttributeValidators(): MutableList<DependencyAttributesValidator> {
        // Currently limited to a single validator
        val validator = EnforcedPlatformPublicationValidator()
        if (suppressedValidationErrors.contains(validator.getSuppressor())) {
            return mutableListOf<DependencyAttributesValidator?>()
        }
        return mutableListOf<DependencyAttributesValidator?>(validator)
    }

    private fun failWith(message: String?, explanation: String? = null) {
        errors.add(message!!)
        if (explanation != null) {
            explanations.add(explanation)
        }
    }

    fun sawVersion() {
        publicationHasVersion = true
    }

    fun sawDependencyOrConstraint() {
        publicationHasDependencyOrConstraint = true
    }

    fun addDependencyValidationError(variant: String?, errorMessage: String?, genericExplanation: String?, suppressor: String) {
        failWith(
            "Variant '" + variant + "' " + errorMessage,
            genericExplanation + explainHowToSuppress(suppressor)
        )
    }

    private fun explainHowToSuppress(suppressor: String): String {
        return " If you did this intentionally you can disable this check by adding '" + suppressor + "' to the suppressed validations of the " + taskPath + " task. " +
                DOCUMENTATION_REGISTRY.getDocumentationRecommendationFor("on suppressing validations", "publishing_setup", "sec:suppressing_validation_errors")
    }

    private class VariantIdentity(private val attributes: AttributeContainer?, private val capabilities: MutableSet<out Capability?>?) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as VariantIdentity
            return Objects.equal(attributes, that.attributes) &&
                    Objects.equal(capabilities, that.capabilities)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(attributes, capabilities)
        }
    }

    companion object {
        private const val DEPENDENCIES_WITHOUT_VERSION_SUPPRESSION = "dependencies-without-versions"

        private val DOCUMENTATION_REGISTRY = DocumentationRegistry()
    }
}
