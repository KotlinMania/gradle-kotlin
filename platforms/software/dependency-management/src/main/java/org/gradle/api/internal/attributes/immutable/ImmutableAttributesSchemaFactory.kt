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
package org.gradle.api.internal.attributes.immutable

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.InMemoryInterner
import org.gradle.internal.model.InMemoryLoadingCache
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Function

/**
 * Factory for creating and interning immutable attribute schemas.
 */
@ServiceScope(Scope.BuildSession::class)
class ImmutableAttributesSchemaFactory(cacheFactory: InMemoryCacheFactory) {
    private val schemas: InMemoryInterner<ImmutableAttributesSchema>
    private val mergedSchemas: InMemoryLoadingCache<SchemaPair, ImmutableAttributesSchema>

    init {
        this.schemas = cacheFactory.createInterner<ImmutableAttributesSchema>()
        this.schemas.intern(ImmutableAttributesSchema.Companion.EMPTY)
        this.mergedSchemas = cacheFactory.create<SchemaPair, ImmutableAttributesSchema>(Function { pair: SchemaPair -> this.doConcatSchemas(pair) })
    }

    /**
     * Create an immutable schema from its raw components, interning the result.
     *
     * @param strategies The attribute matching strategies.
     * @param precedence The attribute matching precedence. Order is significant. Must not contain duplicates.
     *
     * @return The new immutable schema.
     */
    fun create(
        strategies: ImmutableMap<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>>,
        precedence: ImmutableList<Attribute<*>>
    ): ImmutableAttributesSchema {
        return schemas.intern(
            ImmutableAttributesSchema(
                strategies,
                precedence
            )
        )
    }

    /**
     * Create a new immutable schema from the given mutable schema, interning the result.
     *
     * @param mutable The mutable schema to convert.
     *
     * @return The new immutable schema.
     */
    fun create(mutable: AttributesSchemaInternal): ImmutableAttributesSchema {
        // TODO: "Lock in" the mutable schema once we create an immutable copy of it,
        // as to prevent further mutations that will be ignored.
        return create(
            convertStrategies(mutable),
            ImmutableList.copyOf<Attribute<*>>(mutable.getAttributePrecedence())
        )
    }

    /**
     * Merges two immutable schemas into a single schema, interning the result.
     *
     * @param consumer The schema from the consumer side.
     * @param producer The schema from the producer side.
     *
     * @return The merged schema.
     */
    fun concat(consumer: ImmutableAttributesSchema, producer: ImmutableAttributesSchema): ImmutableAttributesSchema {
        return mergedSchemas.get(SchemaPair(consumer, producer))
    }

    private fun doConcatSchemas(pair: SchemaPair): ImmutableAttributesSchema {
        return create(
            mergeStrategies(pair.consumer, pair.producer),
            Companion.mergePrecedence<Attribute<*>>(pair.consumer.precedence, pair.producer.precedence)
        )
    }

    private class SchemaPair(private val consumer: ImmutableAttributesSchema, private val producer: ImmutableAttributesSchema) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(consumer, producer)
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        override fun equals(obj: Any): Boolean {
            if (this === obj) {
                return true
            }
            if (obj == null || javaClass != obj.javaClass) {
                return false
            }
            val other = obj as SchemaPair
            // We expect the consumer and producer to be interned
            return consumer === other.consumer && producer === other.producer
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(consumer: ImmutableAttributesSchema, producer: ImmutableAttributesSchema): Int {
                var result = consumer.hashCode()
                result = 31 * result + producer.hashCode()
                return result
            }
        }
    }

    companion object {
        private fun convertStrategies(mutable: AttributesSchemaInternal): ImmutableMap<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>> {
            val strategies = ImmutableMap.builder<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>>()
            for (entry in mutable.getStrategies().entries) {
                strategies.put(entry.key, Companion.convertStrategy(entry.value))
            }
            return strategies.build()
        }

        private fun <T> convertStrategy(
            mutableStrategy: DefaultAttributeMatchingStrategy<T?>
        ): ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?> {
            return ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?>(
                ImmutableList.copyOf<Action<in CompatibilityCheckDetails<T?>>>(mutableStrategy.getCompatibilityRules().getRules()),
                ImmutableList.copyOf<Action<in MultipleCandidatesDetails<T?>>>(mutableStrategy.getDisambiguationRules().getRules())
            )
        }

        /**
         * Merge the attributes matching strategies of a consumer and producer schema, with the entries from the
         * consumer taking precedence over the producer.
         */
        private fun mergeStrategies(
            consumer: ImmutableAttributesSchema,
            producer: ImmutableAttributesSchema
        ): ImmutableMap<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>> {
            val builder = ImmutableMap.builder<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>>()
            for (attribute in Sets.union<Attribute<*>>(producer.strategies.keys, consumer.strategies.keys)) {
                builder.put(attribute, Companion.mergeStrategyFor(attribute, consumer, producer))
            }
            return builder.build()
        }

        private fun <T> mergeStrategyFor(
            attribute: Attribute<T?>,
            consumer: ImmutableAttributesSchema,
            producer: ImmutableAttributesSchema
        ): ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?> {
            val consumerStrategy = consumer.getStrategy<T?>(attribute)
            val producerStrategy = producer.getStrategy<T?>(attribute)

            assert(consumerStrategy != null || producerStrategy != null)

            if (consumerStrategy == null) {
                return producerStrategy!!
            } else if (producerStrategy == null) {
                return consumerStrategy
            } else {
                return doMergeStrategies<T?>(consumerStrategy, producerStrategy)
            }
        }

        /**
         * Merge the consumer strategy with another producer strategy, giving priority to rules
         * configured in the consumer strategy.
         */
        fun <T> doMergeStrategies(
            consumer: ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?>,
            producer: ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?>
        ): ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?> {
            return ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T?>(
                ImmutableList.builder<Action<in CompatibilityCheckDetails<T?>>>()
                    .addAll(consumer.compatibilityRules)
                    .addAll(producer.compatibilityRules)
                    .build(),
                ImmutableList.builder<Action<in MultipleCandidatesDetails<T?>>>()
                    .addAll(consumer.disambiguationRules)
                    .addAll(producer.disambiguationRules)
                    .build()
            )
        }

        /**
         * Merge two ordered sets, with the elements from the consumer taking precedence over the producer.
         */
        private fun <T> mergePrecedence(consumer: ImmutableList<T?>, producer: ImmutableList<T?>): ImmutableList<T?> {
            return ImmutableSet.builder<T?>()
                .addAll(consumer)
                .addAll(producer) // "Elements appear in the resulting set in the same order they were first added to the builder"
                .build()
                .asList()
        }
    }
}
