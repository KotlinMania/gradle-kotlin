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
package org.gradle.api.internal.artifacts.type

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.reflect.Instantiator

class DefaultArtifactTypeContainer(instantiator: Instantiator, private val attributesFactory: AttributesFactory?, callbackActionDecorator: CollectionCallbackActionDecorator) :
    AbstractValidatingNamedDomainObjectContainer<ArtifactTypeDefinition?>(
        ArtifactTypeDefinition::class.java, instantiator, callbackActionDecorator
    ), ArtifactTypeContainer {
    override fun doCreate(name: String): ArtifactTypeDefinition {
        return getInstantiator().newInstance<DefaultArtifactTypeDefinition>(DefaultArtifactTypeDefinition::class.java, name, attributesFactory)
    }

    class DefaultArtifactTypeDefinition(private val name: String, attributesFactory: AttributesFactory) : ArtifactTypeDefinition {
        private val attributes: AttributeContainer

        init {
            attributes = attributesFactory.mutable()
        }

        override fun getFileNameExtensions(): MutableSet<String?> {
            return ImmutableSet.of<String?>(name)
        }

        override fun getName(): String {
            return name
        }

        override fun getAttributes(): AttributeContainer {
            return attributes
        }
    }
}
