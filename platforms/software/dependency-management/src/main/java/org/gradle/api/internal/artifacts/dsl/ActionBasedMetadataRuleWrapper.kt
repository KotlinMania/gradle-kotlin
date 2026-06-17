/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.rules.SpecRuleAction

internal class ActionBasedMetadataRuleWrapper(private val ruleAction: SpecRuleAction<in ComponentMetadataDetails?>) : MetadataRuleWrapper {
    override fun getDisplayName(): DisplayName {
        return Describables.of("opaque inline rule")
    }

    override fun isClassBased(): Boolean {
        return false
    }

    override fun getClassRules(): MutableCollection<SpecConfigurableRule>? {
        throw UnsupportedOperationException("This operation is not supported by this implementation")
    }

    override fun addClassRule(ruleAction: SpecConfigurableRule) {
        throw UnsupportedOperationException("This operation is not supported by this implementation")
    }

    override fun getRule(): SpecRuleAction<in ComponentMetadataDetails?> {
        return ruleAction
    }
}
