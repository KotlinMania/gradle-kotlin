/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.IdentityHashMap

@ServiceScope(Scope.BuildTree::class)
class AttributeDesugaring(private val attributesFactory: AttributesFactory) {
    private val desugared = IdentityHashMap<ImmutableAttributes, ImmutableAttributes>()

    /**
     * Desugars attributes so that what we're going to serialize consists only of String or Boolean attributes,
     * and not their original types.
     * @return desugared attributes
     */
    fun desugar(attributes: ImmutableAttributes): ImmutableAttributes {
        if (attributes.isEmpty()) {
            return attributes
        }
        return desugared.computeIfAbsent(attributes) { key: ImmutableAttributes? ->
            val mutable = attributesFactory.mutable()
            val keySet: MutableSet<Attribute<*>> = key!!.keySet()
            for (attribute in keySet) {
                val value: Any? = key.getAttribute(attribute)
                var desugared: Attribute<Any> = uncheckedCast<Attribute<Any>?>(attribute)!!
                if (attribute.getType() == Boolean::class.java || attribute.getType() == String::class.java) {
                    mutable.attribute<Any>(desugared, value!!)
                } else {
                    desugared = uncheckedCast<Attribute<Any>?>(Attribute.of<String>(attribute.getName(), String::class.java))!!
                    mutable.attribute<Any>(desugared, value.toString())
                }
            }
            mutable.asImmutable()
        }
    }

    fun desugarSelector(selector: ComponentSelector): ComponentSelector {
        if (selector is ModuleComponentSelector) {
            val module = selector
            val moduleAttributes = module.getAttributes()
            if (!moduleAttributes.isEmpty()) {
                val attributes = (moduleAttributes as AttributeContainerInternal).asImmutable()
                return DefaultModuleComponentSelector.newSelector(module.getModuleIdentifier(), module.getVersionConstraint(), desugar(attributes), module.getCapabilitySelectors())
            }
        }
        if (selector is DefaultProjectComponentSelector) {
            val projectSelector = selector
            val projectAttributes: AttributeContainer = projectSelector.getAttributes()
            if (!projectAttributes.isEmpty()) {
                val attributes = (projectAttributes as AttributeContainerInternal).asImmutable()
                return DefaultProjectComponentSelector.withAttributes(projectSelector, desugar(attributes))
            }
        }
        return selector
    }
}
