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

import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.type.ArtifactTypeContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

@ServiceScope(Scope.Project::class)
class ArtifactTypeRegistry @Inject constructor(private val instantiator: Instantiator, attributesFactory: AttributesFactory, callbackActionDecorator: CollectionCallbackActionDecorator?) {
    private val attributesFactory: AttributesFactory?
    private val callbackActionDecorator: CollectionCallbackActionDecorator?

    /**
     * Default attributes added to all artifact variants during artifact selection.
     */
    @JvmField
    val defaultArtifactAttributes: AttributeContainerInternal?
    private var artifactTypeDefinitions: ArtifactTypeContainer? = null

    init {
        this.attributesFactory = attributesFactory
        this.callbackActionDecorator = callbackActionDecorator
        this.defaultArtifactAttributes = attributesFactory.mutable()
    }

    val artifactTypeContainer: ArtifactTypeContainer
        get() {
            if (artifactTypeDefinitions == null) {
                artifactTypeDefinitions = instantiator.newInstance<DefaultArtifactTypeContainer?>(
                    DefaultArtifactTypeContainer::class.java,
                    instantiator,
                    attributesFactory,
                    callbackActionDecorator
                )
            }
            return artifactTypeDefinitions
        }

    val artifactTypeMappings: ImmutableMap<String?, ImmutableAttributes?>
        get() {
            if (artifactTypeDefinitions == null) {
                return ImmutableMap.of<String?, ImmutableAttributes?>()
            }

            val builder =
                ImmutableMap.builder<String?, ImmutableAttributes?>()
            for (artifactTypeDefinition in artifactTypeDefinitions) {
                val attributes =
                    (artifactTypeDefinition.getAttributes() as AttributeContainerInternal).asImmutable()
                builder.put(artifactTypeDefinition.getName(), attributes)
            }

            return builder.build()
        }
}
