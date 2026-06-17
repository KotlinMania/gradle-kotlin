/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.VariantMetadataRules
import java.util.LinkedList

/**
 * A set of rules provided by the build script author
 * (as [&amp;lt;? super AttributeContainer&amp;gt;][Action]
 * that are applied on the attributes defined in variant/configuration metadata. The rules are applied
 * in the [.execute] method when the attributes of a variant are needed during dependency resolution.
 */
class VariantAttributesRules(private val attributesFactory: AttributesFactory) {
    private val actions: MutableList<VariantMetadataRules.VariantAction<in AttributeContainer>> = LinkedList<VariantMetadataRules.VariantAction<in AttributeContainer>>()

    fun addAttributesAction(action: VariantMetadataRules.VariantAction<in AttributeContainer>) {
        actions.add(action)
    }

    fun execute(variant: VariantResolveMetadata, attributes: AttributeContainerInternal): ImmutableAttributes {
        val mutable = attributesFactory.mutable(attributes)
        for (action in actions) {
            action.maybeExecute(variant, mutable)
        }
        return mutable.asImmutable()
    }
}
