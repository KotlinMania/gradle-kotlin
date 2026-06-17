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

import com.google.common.collect.Ordering
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.DisambiguationRuleChain
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRules
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.internal.type.ModelType

class DefaultDisambiguationRuleChain<T>(private val instantiator: Instantiator, private val isolatableFactory: IsolatableFactory) : DisambiguationRuleChain<T?> {
    val rules: MutableList<Action<in MultipleCandidatesDetails<T?>>> = ArrayList<Action<in MultipleCandidatesDetails<T?>>>()

    override fun add(ruleClass: Class<out AttributeDisambiguationRule<T?>>, configureAction: Action<in ActionConfiguration>) {
        val rule = DefaultConfigurableRule.of<MultipleCandidatesDetails<T?>>(ruleClass, configureAction, isolatableFactory)
        rules.add(createAction<T?>(rule, instantiator))
    }

    override fun add(ruleClass: Class<out AttributeDisambiguationRule<T?>>) {
        val rule = DefaultConfigurableRule.of<MultipleCandidatesDetails<T?>>(ruleClass)
        rules.add(createAction<T?>(rule, instantiator))
    }

    override fun pickFirst(comparator: Comparator<in T?>) {
        val rule = AttributeMatchingRules.orderedDisambiguation<T?>(comparator, true)
        rules.add(rule)
    }

    override fun pickLast(comparator: Comparator<in T?>) {
        val rule = AttributeMatchingRules.orderedDisambiguation<T?>(comparator, false)
        rules.add(rule)
    }

    private class ExceptionHandler<T>(private val rule: Class<*>) : InstantiatingAction.ExceptionHandler<MultipleCandidatesDetails<T?>> {
        override fun handleException(details: MultipleCandidatesDetails<T?>, throwable: Throwable) {
            val orderedValues: MutableSet<T?> = Sets.newTreeSet<T?>(Ordering.usingToString())
            orderedValues.addAll(details.getCandidateValues())
            throw AttributeMatchException(String.format("Could not select value from candidates %s using %s.", orderedValues, ModelType.of(rule).getDisplayName()), throwable)
        }
    }

    companion object {
        fun <T> createAction(
            rule: ConfigurableRule<MultipleCandidatesDetails<T?>>,
            instantiator: Instantiator
        ): Action<MultipleCandidatesDetails<T?>> {
            return InstantiatingAction<MultipleCandidatesDetails<T?>>(DefaultConfigurableRules.of<MultipleCandidatesDetails<T?>>(rule), instantiator, ExceptionHandler<T?>(rule.getRuleClass()))
        }
    }
}
