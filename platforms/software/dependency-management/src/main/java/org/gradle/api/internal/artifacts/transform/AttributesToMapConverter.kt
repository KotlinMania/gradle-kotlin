/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableMap
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer

/**
 * Converts attributes to a stringy map preserving the order.
 */
object AttributesToMapConverter {
    /**
     * Converts attributes to a stringy map preserving the order.
     */
    fun convertToMap(attributes: AttributeContainer): MutableMap<String, String> {
        val builder = ImmutableMap.builder<String, String>()
        for (attribute in attributes.keySet()) {
            val strValue = getAttributeValueAsString(attributes, attribute)
            builder.put(attribute.getName(), strValue)
        }
        return builder.build()
    }

    private fun getAttributeValueAsString(attributeContainer: AttributeContainer, attribute: Attribute<*>): String {
        // We use the same algorithm that Gradle uses when desugaring these on the build op, so that we don't end up
        // with unexpectedly different values due to arrays or Named objects being converted to Strings differently.
        // See LazyDesugaringAttributeContainer in the gradle/gradle codebase.
        val attributeValue: Any? = attributeContainer.getAttribute(attribute)
        checkNotNull(attributeValue) { "No attribute value for " + attribute }

        if (attributeValue is Named) {
            return attributeValue.getName()
        } else if (attributeValue is Array<Any>) {
            // don't bother trying to handle primitive arrays specially
            return (attributeValue as Array<Any?>).contentToString()
        } else {
            return attributeValue.toString()
        }
    }
}
