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

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.rules.SpecRuleAction
import java.util.stream.Collectors

internal class ClassBasedMetadataRuleWrapper(classRule: SpecConfigurableRule) : MetadataRuleWrapper {
    private val classRules: MutableList<SpecConfigurableRule> = ArrayList<SpecConfigurableRule>(5)

    init {
        this.classRules.add(classRule)
    }

    override fun isClassBased(): Boolean {
        return true
    }

    override fun getClassRules(): MutableCollection<SpecConfigurableRule> {
        return classRules
    }

    override fun addClassRule(classRule: SpecConfigurableRule) {
        classRules.add(classRule)
    }

    override fun getRule(): SpecRuleAction<in ComponentMetadataDetails?>? {
        throw UnsupportedOperationException("This operation is not supported by this implementation")
    }

    override fun getDisplayName(): DisplayName {
        return Describables.of(
            classRules.stream()
                .map<ConfigurableRule<ComponentMetadataContext>> { obj: SpecConfigurableRule? -> obj!!.getConfigurableRule() }
                .map { obj: ConfigurableRule<ComponentMetadataContext?>? -> obj!!.getRuleClass() }
                .map<String> { obj: Class<Action<ComponentMetadataContext?>?>? -> obj!!.getName() }
                .collect(Collectors.joining(",")))
    }
}
