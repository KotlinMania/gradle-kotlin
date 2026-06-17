/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.publish.internal.component.ConfigurationVariantDetailsInternal
import org.gradle.api.specs.Spec

class JavaConfigurationVariantMapping @JvmOverloads constructor(private val scope: String, private val optional: Boolean, private val resolutionConfiguration: Configuration? = null) :
    Action<ConfigurationVariantDetails?> {
    override fun execute(details: ConfigurationVariantDetails) {
        val variant = details.getConfigurationVariant()
        if (UnpublishableArtifactTypeSpec.Companion.INSTANCE.isSatisfiedBy(variant)) {
            details.skip()
        } else {
            details.mapToMavenScope(scope)
            if (optional) {
                details.mapToOptional()
            }
            if (resolutionConfiguration != null) {
                (details as ConfigurationVariantDetailsInternal).dependencyMapping(Action { dependencyMapping: ConfigurationVariantDetailsInternal.DependencyMappingDetails? ->
                    dependencyMapping!!.fromResolutionOf(resolutionConfiguration)
                })
            }
        }
    }

    private class UnpublishableArtifactTypeSpec : Spec<ConfigurationVariant?> {
        override fun isSatisfiedBy(element: ConfigurationVariant): Boolean {
            for (artifact in element.getArtifacts()) {
                if (UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.getType())) {
                    return true
                }
            }
            return false
        }

        companion object {
            private val INSTANCE = UnpublishableArtifactTypeSpec()
        }
    }

    companion object {
        /**
         * A list of known artifact types which are known to prevent from
         * publication.
         */
        @JvmField
        val UNPUBLISHABLE_VARIANT_ARTIFACTS: MutableSet<String?> = ImmutableSet.of<String?>(
            ArtifactTypeDefinition.JVM_CLASS_DIRECTORY,
            ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY,
            ArtifactTypeDefinition.DIRECTORY_TYPE
        )
    }
}
