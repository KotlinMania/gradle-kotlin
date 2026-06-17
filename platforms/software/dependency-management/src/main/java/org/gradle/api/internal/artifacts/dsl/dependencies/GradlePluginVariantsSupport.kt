/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.util.GradleVersion

object GradlePluginVariantsSupport {
    fun configureSchema(attributesSchema: AttributesSchema) {
        val strategy = attributesSchema.attribute<GradlePluginApiVersion>(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE)
        strategy.getCompatibilityRules().add(TargetGradleVersionCompatibilityRule::class.java)
        strategy.getDisambiguationRules().add(TargetGradleVersionDisambiguationRule::class.java)
    }

    fun configureFailureHandler(handler: ResolutionFailureHandler) {
        handler.addFailureDescriber<NoCompatibleVariantsFailure?>(NoCompatibleVariantsFailure::class.java, NewerGradleNeededByPluginFailureDescriber::class.java)
        handler.addFailureDescriber<NoCompatibleVariantsFailure?>(NoCompatibleVariantsFailure::class.java, TargetJVMVersionOnPluginTooNewFailureDescriber::class.java)
    }

    class TargetGradleVersionCompatibilityRule : AttributeCompatibilityRule<GradlePluginApiVersion?> {
        override fun execute(details: CompatibilityCheckDetails<GradlePluginApiVersion>) {
            // we compare to the base version of the consumer, because pre-release versions should already match variants targeting the final release
            val consumer = details.getConsumerValue()
            val producer = details.getProducerValue()
            if (consumer == null || producer == null) {
                details.compatible()
            } else if (GradleVersion.version(consumer.getName()).getBaseVersion().compareTo(GradleVersion.version(producer.getName())) >= 0) {
                details.compatible()
            } else {
                details.incompatible()
            }
        }
    }

    class TargetGradleVersionDisambiguationRule : AttributeDisambiguationRule<GradlePluginApiVersion?> {
        override fun execute(details: MultipleCandidatesDetails<GradlePluginApiVersion>) {
            val consumerValue = details.getConsumerValue()
            val consumer = if (consumerValue == null) GradleVersion.current() else GradleVersion.version(consumerValue.getName())
            var bestMatchVersion = GradleVersion.version("0.0")
            var bestMatchAttribute: GradlePluginApiVersion? = null

            for (candidate in details.getCandidateValues()) {
                val producer = GradleVersion.version(candidate.getName())
                if (producer.compareTo(consumer) <= 0 && producer.compareTo(bestMatchVersion) > 0) {
                    bestMatchVersion = producer
                    bestMatchAttribute = candidate
                }
            }
            if (bestMatchAttribute != null) {
                details.closestMatch(bestMatchAttribute)
            }
        }
    }
}
