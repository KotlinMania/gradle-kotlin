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
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.NoOpDerivationStrategy
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.rules.SpecRuleAction
import java.util.function.Consumer

/**
 * Container for registered ComponentMetadataRules, either class based or closure / action based.
 */
internal class ComponentMetadataRuleContainer : Iterable<MetadataRuleWrapper> {
    private val rules: MutableList<MetadataRuleWrapper> = ArrayList<MetadataRuleWrapper>(10)
    private var lastAdded: MetadataRuleWrapper? = null
    var isClassBasedRulesOnly: Boolean = true
        private set
    var variantDerivationStrategy: VariantDerivationStrategy = NoOpDerivationStrategy.getInstance()
    private var rulesHash = 0
    private var onAdd: Consumer<DisplayName>? = null

    fun addRule(ruleAction: SpecRuleAction<in ComponentMetadataDetails?>) {
        lastAdded = ActionBasedMetadataRuleWrapper(ruleAction)
        addRule()
        this.isClassBasedRulesOnly = false
        rulesHash = 31 * rulesHash + ruleAction.hashCode()
    }

    private fun addRule() {
        if (onAdd != null) {
            onAdd!!.accept(lastAdded!!.getDisplayName())
        }
        rules.add(lastAdded!!)
    }

    fun addClassRule(ruleAction: SpecConfigurableRule) {
        if (lastAdded != null && lastAdded!!.isClassBased()) {
            lastAdded!!.addClassRule(ruleAction)
        } else {
            lastAdded = ClassBasedMetadataRuleWrapper(ruleAction)
            addRule()
        }
        rulesHash = 31 * rulesHash + ruleAction.getConfigurableRule().hashCode()
    }

    val isEmpty: Boolean
        get() = rules.isEmpty()

    override fun iterator(): MutableIterator<MetadataRuleWrapper> {
        return rules.iterator()
    }

    val onlyClassRules: MutableCollection<SpecConfigurableRule>
        get() {
            check(!(!this.isClassBasedRulesOnly || this.isEmpty)) { "This method cannot be used unless there is at least one rule and they are all class based" }
            return rules.get(0).getClassRules()
        }

    fun getRulesHash(): Int {
        return 31 * variantDerivationStrategy.hashCode() + rulesHash
    }

    fun onAddRule(consumer: Consumer<DisplayName>) {
        this.onAdd = consumer
    }
}
