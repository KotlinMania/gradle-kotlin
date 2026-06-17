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

import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory

class DefaultAttributeMatchingStrategy<T>(instantiatorFactory: InstantiatorFactory, isolatableFactory: IsolatableFactory) : AttributeMatchingStrategy<T?> {
    private val compatibilityRules: DefaultCompatibilityRuleChain<T?>
    private val disambiguationRules: DefaultDisambiguationRuleChain<T?>

    init {
        compatibilityRules =
            instantiatorFactory.decorateLenient().newInstance<DefaultCompatibilityRuleChain<*>>(DefaultCompatibilityRuleChain::class.java, instantiatorFactory.inject(), isolatableFactory)
        disambiguationRules =
            instantiatorFactory.decorateLenient().newInstance<DefaultDisambiguationRuleChain<*>>(DefaultDisambiguationRuleChain::class.java, instantiatorFactory.inject(), isolatableFactory)
    }

    override fun getCompatibilityRules(): DefaultCompatibilityRuleChain<T?> {
        return compatibilityRules
    }

    override fun getDisambiguationRules(): DefaultDisambiguationRuleChain<T?> {
        return disambiguationRules
    }

    override fun ordered(comparator: Comparator<T?>) {
        ordered(true, comparator)
    }

    override fun ordered(pickLast: Boolean, comparator: Comparator<T?>) {
        compatibilityRules.ordered(comparator)
        if (pickLast) {
            disambiguationRules.pickLast(comparator)
        } else {
            disambiguationRules.pickFirst(comparator)
        }
    }
}
