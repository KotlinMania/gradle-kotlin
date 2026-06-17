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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import java.util.Optional

class EnforcedPlatformPublicationValidator : DependencyAttributesValidator {
    override fun getSuppressor(): String {
        return SUPPRESSION
    }

    override fun getExplanation(): String {
        return LONG_EXPLANATION
    }

    override fun validationErrorFor(group: String?, name: String?, attributes: AttributeContainer): Optional<String?> {
        val category = attributes.getAttribute<Category?>(Category.CATEGORY_ATTRIBUTE)
        if (category != null) {
            if (Category.ENFORCED_PLATFORM == category.getName()) {
                return Optional.of<String?>("contains a dependency on enforced platform '" + group + ":" + name + "'")
            }
        }
        return Optional.empty<String?>()
    }

    companion object {
        private const val SUPPRESSION = "enforced-platform"
        private val LONG_EXPLANATION = "In general publishing dependencies to enforced platforms is a mistake: " +
                "enforced platforms shouldn't be used for published components because they behave like forced dependencies and leak to consumers. " +
                "This can result in hard to diagnose dependency resolution errors."
    }
}
