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
package org.gradle.api.internal.artifacts.configurations

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.Named
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Actions
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization
import java.util.Collections
import java.util.function.Supplier

internal class ResolveConfigurationResolutionBuildOperationResult(
    private val graphSource: Supplier<ResolvedGraphResult>,
    requestedAttributes: ImmutableAttributes,
    attributesFactory: AttributesFactory
) : ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {
    private val lazyDesugaredAttributes: Lazy<AttributeContainer?>

    init {
        this.lazyDesugaredAttributes = locking().of<AttributeContainer?>(Supplier { desugarAttributes(attributesFactory, requestedAttributes) })
    }

    override fun getRootComponent(): ResolvedComponentResult {
        val graph = graphSource.get()
        val nodes = graph.structure().nodes()
        return graph.getComponent(nodes!!.owner(nodes.root()))
    }

    override fun getRepositoryId(resolvedComponentResult: ResolvedComponentResult): String {
        return (resolvedComponentResult as ResolvedComponentResultInternal).repositoryId!!
    }

    override fun getCustomOperationTraceSerializableModel(): Any {
        val model: MutableMap<String, Any> = HashMap<String, Any>()
        model.put("resolvedDependenciesCount", getRootComponent().getDependencies().size)

        val components: MutableMap<String, MutableMap<String, String>> = HashMap<String, MutableMap<String, String>>()
        eachElement(getRootComponent(), { component ->
            components.put(
                component.getId().getDisplayName(),
                Collections.singletonMap<String, String>("repoId", getRepositoryId(component))
            )
        }, Actions.doNothing<T>(), HashSet<E?>())
        model.put("components", components)

        val requestedAttributesBuilder = ImmutableList.Builder<Any>()
        val desugared: AttributeContainer = lazyDesugaredAttributes.get()!!
        for (att in desugared.keySet()) {
            requestedAttributesBuilder.add(ImmutableMap.of<String, String>("name", att.getName(), "value", desugared.getAttribute(att).toString()))
        }
        model.put("requestedAttributes", requestedAttributesBuilder.build())

        return model
    }

    override fun getRequestedAttributes(): AttributeContainer {
        return lazyDesugaredAttributes.get()!!
    }

    companion object {
        // This does almost the same thing as passing through DesugaredAttributeContainerSerializer / DesugaringAttributeContainerSerializer.
        // Those make some assumptions about allowed attribute value types that we can't - we serialize everything else to a string instead.
        private fun desugarAttributes(
            attributesFactory: AttributesFactory,
            source: AttributeContainer
        ): ImmutableAttributes {
            val result = attributesFactory.mutable()
            for (attribute in source.keySet()) {
                val name = attribute.getName()
                val type: Class<*> = attribute.getType()
                val attributeValue: Any? = source.getAttribute(attribute)
                if (type == Boolean::class.java) {
                    result.attribute<Boolean>(attribute as Attribute<Boolean?>, (attributeValue as kotlin.Boolean?)!!)
                } else if (type == String::class.java) {
                    result.attribute<String>(attribute as Attribute<String?>, (attributeValue as kotlin.String?)!!)
                } else if (type == Int::class.java) {
                    result.attribute<Int>(attribute as Attribute<Int?>, (attributeValue as kotlin.Int?)!!)
                } else {
                    // just serialize as a String as best we can
                    val stringAtt = Attribute.of<String>(name, String::class.java)
                    val stringValue: String
                    if (attributeValue is Named) {
                        stringValue = attributeValue.getName()
                    } else if (attributeValue is Array<Any>) { // don't bother trying to handle primitive arrays specially
                        stringValue = (attributeValue as Array<Any?>).contentToString()
                    } else {
                        stringValue = attributeValue.toString()
                    }
                    result.attribute<String>(stringAtt, stringValue)
                }
            }

            return result.asImmutable()
        }
    }
}
