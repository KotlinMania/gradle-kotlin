/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistryFactory
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.attributes.matching.CachingAttributeSelectionSchema
import org.gradle.api.internal.attributes.matching.DefaultAttributeMatcher
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.InMemoryLoadingCache
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Function
import javax.inject.Inject

/**
 * A collection of services for creating and working with immutable attribute schemas.
 */
@ServiceScope(Scope.BuildSession::class)
class AttributeSchemaServices @Inject constructor(
    /**
     * Get a factory that can produce immutable attribute schemas from mutable ones.
     */
    val schemaFactory: ImmutableAttributesSchemaFactory,
    /**
     * Get a factory that can produce immutable artifact type registries from mutable ones.
     */
    val artifactTypeRegistryFactory: ImmutableArtifactTypeRegistryFactory,
    private val cacheFactory: InMemoryCacheFactory
) {
    private val matchers: InMemoryLoadingCache<ImmutableAttributesSchema, AttributeMatcher>

    init {
        this.matchers = cacheFactory.createIdentityCache<ImmutableAttributesSchema, AttributeMatcher>(Function { schema: ImmutableAttributesSchema -> this.createMatcher(schema) })
    }

    /**
     * Given a consumer and producer attribute schema, returns a matcher that can
     * be used to match attributes from the two schemas.
     *
     *
     * Returned matchers are cached and reused for the same input schemas.
     */
    fun getMatcher(consumer: ImmutableAttributesSchema, producer: ImmutableAttributesSchema): AttributeMatcher {
        val merged = schemaFactory.concat(consumer, producer)
        return matchers.get(merged)
    }

    private fun createMatcher(schema: ImmutableAttributesSchema): DefaultAttributeMatcher {
        return DefaultAttributeMatcher(
            CachingAttributeSelectionSchema(
                DefaultAttributeSelectionSchema(schema),
                cacheFactory
            ),
            cacheFactory
        )
    }
}
