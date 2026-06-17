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
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.isolation.IsolatableFactory
import java.util.Collections
import javax.inject.Inject

open class DefaultAttributesSchema @Inject constructor(private val instantiatorFactory: InstantiatorFactory, private val isolatableFactory: IsolatableFactory) : AttributesSchemaInternal {
    private val strategies: MutableMap<Attribute<*>, DefaultAttributeMatchingStrategy<*>> = HashMap<Attribute<*>, DefaultAttributeMatchingStrategy<*>>()
    private val precedence: MutableSet<Attribute<*>> = LinkedHashSet<Attribute<*>>()

    // region public API
    override fun <T> getMatchingStrategy(attribute: Attribute<T?>): AttributeMatchingStrategy<T?> {
        val strategy: AttributeMatchingStrategy<T?> = strategies.get(attribute) as DefaultAttributeMatchingStrategy<T?>
        requireNotNull(strategy) { "Unable to find matching strategy for " + attribute }
        return strategy
    }

    override fun <T> attribute(attribute: Attribute<T?>): AttributeMatchingStrategy<T?> {
        return attribute<T?>(attribute, null)
    }

    override fun <T> attribute(attribute: Attribute<T?>, configureAction: Action<in AttributeMatchingStrategy<T?>>?): AttributeMatchingStrategy<T?> {
        val strategy = getStrategy<T?>(attribute)
        if (configureAction != null) {
            configureAction.execute(strategy)
        }
        return strategy
    }

    private fun <T> getStrategy(attribute: Attribute<T?>): AttributeMatchingStrategy<T?> {
        var strategy = strategies.get(attribute) as DefaultAttributeMatchingStrategy<T?>?
        if (strategy == null) {
            strategy = instantiatorFactory.decorateLenient().newInstance<DefaultAttributeMatchingStrategy<*>>(DefaultAttributeMatchingStrategy::class.java, instantiatorFactory, isolatableFactory)
            strategies.put(attribute, strategy)
        }
        return strategy
    }

    override fun getAttributes(): MutableSet<Attribute<*>> {
        return strategies.keys
    }

    override fun hasAttribute(key: Attribute<*>): Boolean {
        return strategies.containsKey(key)
    }

    override fun attributeDisambiguationPrecedence(vararg attributes: Attribute<*>) {
        for (attribute in attributes) {
            require(precedence.add(attribute)) { String.format("Attribute '%s' precedence has already been set.", attribute.getName()) }
        }
    }

    override fun setAttributeDisambiguationPrecedence(attributes: MutableList<Attribute<*>>) {
        precedence.clear()
        attributeDisambiguationPrecedence(*attributes.toTypedArray<Attribute<*>>())
    }

    override fun getAttributeDisambiguationPrecedence(): MutableList<Attribute<*>> {
        return Collections.unmodifiableList<Attribute<*>>(ArrayList<Attribute<*>?>(precedence))
    }

    // endregion
    override fun getStrategies(): MutableMap<Attribute<*>, DefaultAttributeMatchingStrategy<*>> {
        return Collections.unmodifiableMap<Attribute<*>, DefaultAttributeMatchingStrategy<*>>(strategies)
    }


    override fun getAttributePrecedence(): MutableSet<Attribute<*>> {
        return Collections.unmodifiableSet<Attribute<*>>(precedence)
    }
}
