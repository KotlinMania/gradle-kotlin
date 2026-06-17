/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.attributes

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.CompatibilityRuleChain
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.internal.type.ModelType

class DefaultCompatibilityRuleChain<T>(private val instantiator: Instantiator, private val isolatableFactory: IsolatableFactory) : CompatibilityRuleChain<T?> {
    val rules: MutableList<Action<in CompatibilityCheckDetails<T?>>> = ArrayList<Action<in CompatibilityCheckDetails<T?>>>()

    override fun ordered(comparator: Comparator<in T?>) {
        val rule = AttributeMatchingRules.orderedCompatibility<T?>(comparator, false)
        rules.add(rule)
    }

    override fun reverseOrdered(comparator: Comparator<in T?>) {
        val rule = AttributeMatchingRules.orderedCompatibility<T?>(comparator, true)
        rules.add(rule)
    }

    override fun add(ruleClass: Class<out AttributeCompatibilityRule<T?>>, configureAction: Action<in ActionConfiguration>) {
        val rule = DefaultConfigurableRule.of<CompatibilityCheckDetails<T?>>(ruleClass, configureAction, isolatableFactory)
        rules.add(createAction<T?>(rule, instantiator))
    }

    override fun add(ruleClass: Class<out AttributeCompatibilityRule<T?>>) {
        val rule = DefaultConfigurableRule.of<CompatibilityCheckDetails<T?>>(ruleClass)
        rules.add(createAction<T?>(rule, instantiator))
    }

    private class ExceptionHandler<T>(private val rule: Class<*>) : InstantiatingAction.ExceptionHandler<CompatibilityCheckDetails<T?>> {
        override fun handleException(details: CompatibilityCheckDetails<T?>, throwable: Throwable) {
            throw AttributeMatchException(
                String.format(
                    "Could not determine whether value %s is compatible with value %s using %s.",
                    details.getProducerValue(),
                    details.getConsumerValue(),
                    ModelType.of(rule).getDisplayName()
                ), throwable
            )
        }
    }

    companion object {
        fun <T> createAction(
            rule: ConfigurableRule<CompatibilityCheckDetails<T?>>,
            instantiator: Instantiator
        ): Action<CompatibilityCheckDetails<T?>> {
            return InstantiatingAction<CompatibilityCheckDetails<T?>>(DefaultConfigurableRules.of<CompatibilityCheckDetails<T?>>(rule), instantiator, ExceptionHandler<T?>(rule.getRuleClass()))
        }
    }
}
