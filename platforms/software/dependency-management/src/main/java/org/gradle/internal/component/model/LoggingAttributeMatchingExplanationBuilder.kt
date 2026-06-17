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
package org.gradle.internal.component.model

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesEntry
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger

class LoggingAttributeMatchingExplanationBuilder : AttributeMatchingExplanationBuilder {
    override fun noCandidates(requested: ImmutableAttributes) {
        LOGGER.debug("No candidates for {}. Select nothing.", requested)
    }

    override fun singleMatch(candidate: ImmutableAttributes, candidates: MutableCollection<ImmutableAttributes>, requested: AttributeContainerInternal) {
        LOGGER.debug("Selected match {} from candidates {} for {}", candidate, candidates, requested)
    }

    override fun candidateDoesNotMatchAttributes(candidate: ImmutableAttributes, requested: AttributeContainerInternal) {
        LOGGER.debug("Candidate {} doesn't match attributes {}", candidate, requested)
    }

    override fun candidateAttributeDoesNotMatch(candidate: ImmutableAttributes, attribute: Attribute<*>, requestedValue: Any, candidateEntry: ImmutableAttributesEntry<*>) {
        LOGGER.debug("Candidate {} attribute {} value {} doesn't requested value {}", candidate, attribute, candidateEntry, requestedValue)
    }

    override fun candidateAttributeMissing(candidate: ImmutableAttributes, attribute: Attribute<*>, requestedValue: Any) {
        LOGGER.debug("Candidate {} doesn't have attribute {}", candidate, attribute)
    }

    override fun candidateIsSuperSetOfAllOthers(candidate: ImmutableAttributes) {
        LOGGER.debug("Candidate {} selected because its attributes are a superset of all other candidate attributes", candidate)
    }

    companion object {
        private val INSTANCE: AttributeMatchingExplanationBuilder = LoggingAttributeMatchingExplanationBuilder()
        private val LOGGER: Logger = getLogger(LoggingAttributeMatchingExplanationBuilder::class.java)!!

        fun logging(): AttributeMatchingExplanationBuilder {
            if (LOGGER.isDebugEnabled()) {
                return INSTANCE
            }
            return AttributeMatchingExplanationBuilder.Companion.NO_OP
        }
    }
}
