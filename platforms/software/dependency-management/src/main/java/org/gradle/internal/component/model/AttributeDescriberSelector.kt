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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeDescriber
import java.util.function.Function

object AttributeDescriberSelector {
    fun selectDescriber(consumerAttributes: AttributeContainerInternal, attributeDescribers: MutableList<AttributeDescriber>): AttributeDescriber {
        val consumerAttributeSet = consumerAttributes.keySet()
        var current: AttributeDescriber? = null
        var maxSize = 0
        for (describer in attributeDescribers) {
            val size = Sets.intersection<Attribute<*>>(describer.getDescribableAttributes(), consumerAttributeSet).size
            if (size > maxSize) {
                // Select the describer which handles the maximum number of attributes
                current = describer
                maxSize = size
            }
        }
        if (current != null) {
            return FallbackDescriber(current)
        }
        return DefaultDescriber.Companion.INSTANCE
    }

    private class FallbackDescriber(private val delegate: AttributeDescriber) : AttributeDescriber {
        override fun getDescribableAttributes(): ImmutableSet<Attribute<*>> {
            return delegate.getDescribableAttributes()
        }

        override fun describeAttributeSet(attributes: MutableMap<Attribute<*>, *>): String {
            val description = delegate.describeAttributeSet(attributes)
            return if (description == null) DefaultDescriber.Companion.INSTANCE.describeAttributeSet(attributes) else description
        }

        override fun describeMissingAttribute(attribute: Attribute<*>, producerValue: Any): String {
            val description = delegate.describeMissingAttribute(attribute, producerValue)
            return if (description == null) DefaultDescriber.Companion.INSTANCE.describeMissingAttribute(attribute, producerValue) else description
        }

        override fun describeExtraAttribute(attribute: Attribute<*>, producerValue: Any): String {
            val description = delegate.describeExtraAttribute(attribute, producerValue)
            return if (description == null) DefaultDescriber.Companion.INSTANCE.describeExtraAttribute(attribute, producerValue) else description
        }
    }

    private class DefaultDescriber : AttributeDescriber {
        override fun getDescribableAttributes(): ImmutableSet<Attribute<*>> {
            return ImmutableSet.of<Attribute<*>>()
        }

        override fun describeAttributeSet(attributes: MutableMap<Attribute<*>, *>): String {
            val sb = StringBuilder()
            attributes.entries.stream()
                .sorted(Comparator.comparing(Function { e: MutableMap.MutableEntry<Attribute<*>?, Any?>? -> e!!.key!!.getName() }))
                .forEach { entry: MutableMap.MutableEntry<Attribute<*>?, Any?>? ->
                    val attribute: Attribute<*> = entry!!.key!!
                    if (sb.length > 0) {
                        sb.append(", ")
                    }
                    sb.append("attribute '").append(attribute.getName()).append("' with value '").append(entry.value).append("'")
                }
            return sb.toString()
        }

        override fun describeMissingAttribute(attribute: Attribute<*>, consumerValue: Any): String {
            return attribute.getName() + " (required '" + consumerValue + "')"
        }

        override fun describeExtraAttribute(attribute: Attribute<*>, producerValue: Any): String {
            return attribute.getName() + " '" + producerValue + "'"
        }

        companion object {
            private val INSTANCE = DefaultDescriber()
        }
    }
}
