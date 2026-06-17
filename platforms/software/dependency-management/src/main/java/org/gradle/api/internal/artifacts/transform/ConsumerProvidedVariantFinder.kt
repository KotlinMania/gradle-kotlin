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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.internal.collections.ImmutableFilteredList
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Supplier

/**
 * Finds all the variants that can be created from a given set of producer variants using
 * the consumer's variant transforms. Transforms can be chained. If multiple
 * chains can lead to the same outcome, the shortest paths are selected.
 *
 * Caches the results, as often the same request is made for many components in a
 * dependency graph.
 */
@ServiceScope(Scope.Project::class)
class ConsumerProvidedVariantFinder(
    private val variantTransforms: VariantTransformRegistry,
    schema: AttributesSchemaInternal,
    private val attributesFactory: AttributesFactory,
    attributeSchemaServices: AttributeSchemaServices
) {
    private val matcher: Lazy<AttributeMatcher?>
    private val transformCache: TransformCache

    init {
        this.matcher = locking().of<AttributeMatcher?>(Supplier {
            // TODO: This is incorrect. We fail to merge the consumer schema with the producer schema
            // and therefore we miss producer rules when matching transforms.
            // Instead, this class should be refactored to accept a matcher as a parameter,
            // where the matcher has already been created with the consumer and producer schema.
            val immutable = attributeSchemaServices.schemaFactory.create(schema)
            attributeSchemaServices.getMatcher(immutable, ImmutableAttributesSchema.EMPTY)
        })
        this.transformCache = TransformCache(BiFunction { sources: MutableList<ImmutableAttributes>, requested: ImmutableAttributes -> this.doFindTransformedVariants(sources, requested) })
    }

    /**
     * Executes the transform chain detection algorithm given a set of producer variants and the requested
     * attributes. Only the transform chains of the shortest depth are returned, and all results are
     * guaranteed to have the same depth.
     *
     * @param sources The set of producer variants.
     * @param requested The requested attributes.
     *
     * @return A collection of variant chains which, if applied to the corresponding source variant, will produce a
     * variant compatible with the requested attributes.
     */
    fun findCandidateTransformationChains(sources: MutableList<ResolvedVariant>, requested: ImmutableAttributes): MutableList<TransformedVariant> {
        return transformCache.query(sources, requested)
    }

    /**
     * A node in a chain of artifact transforms.
     */
    private class ChainNode(val next: ChainNode?, val transform: TransformRegistration)

    /**
     * Represents the intermediate state of a potential transform solution. Many instances of this state may simultaneously exist
     * for different potential solutions.
     */
    private class ChainState
    /**
     * @param chain The candidate transform chain.
     * @param requested The attribute set which must be produced by any previous variant in order to achieve the
     * original user-requested attribute set after `chain` is applied to that previous variant.
     * @param transforms The remaining transforms which may be prepended to `chain` to produce a solution.
     */(val chain: ChainNode?, val requested: ImmutableAttributes, val transforms: ImmutableFilteredList<TransformRegistration>)

    /**
     * A cached result of the transform chain detection algorithm. References an index within the source variant
     * list instead of an actual variant itself, so that this result can be cached and used for distinct variant sets
     * that otherwise share the same attributes.
     */
    private class CachedVariant(private val sourceIndex: Int, private val chain: VariantDefinition)

    /**
     * The algorithm itself. Performs a breadth-first search on the set of potential transform solutions in order to find
     * all solutions at a given transform chain depth. The search begins at the final node of the chain. At each depth, a candidate
     * transform is applied to the beginning of the chain. Then, if a source variant can be used as a root of that chain,
     * we have found a solution. Otherwise, if no solutions are found at this depth, we run the search at the next depth, with all
     * candidate transforms linked to the previous level's chains.
     */
    private fun doFindTransformedVariants(sources: MutableList<ImmutableAttributes>, requested: ImmutableAttributes): MutableList<CachedVariant> {
        val attributeMatcher: AttributeMatcher = matcher.get()!!

        var toProcess: MutableList<ChainState> = ArrayList<ChainState>()
        var nextDepth: MutableList<ChainState> = ArrayList<ChainState>()
        toProcess.add(ChainState(null, requested, ImmutableFilteredList.allOf<TransformRegistration>(ArrayList<TransformRegistration?>(variantTransforms.registrations))))

        val results: MutableList<CachedVariant> = ArrayList<CachedVariant>(1)
        while (results.isEmpty() && !toProcess.isEmpty()) {
            for (state in toProcess) {
                // The set of transforms which could potentially produce a variant compatible with `requested`.
                val candidates =
                    state.transforms.matching(org.gradle.api.specs.Spec { transform: TransformRegistration? -> attributeMatcher.isMatchingCandidate(transform!!.to, state.requested) })

                // For each candidate, attempt to find a source variant that the transform can use as its root.
                for (candidate in candidates) {
                    for (i in sources.indices) {
                        val sourceAttrs = sources.get(i)
                        if (attributeMatcher.isMatchingCandidate(sourceAttrs, candidate.from)) {
                            val rootAttrs = attributesFactory.concat(sourceAttrs, candidate.to)
                            if (attributeMatcher.isMatchingCandidate(rootAttrs, state.requested)) {
                                val rootTransformedVariant = DefaultVariantDefinition(null, rootAttrs, candidate.transformStep)
                                val variantChain = createVariantChain(state.chain!!, rootTransformedVariant)
                                results.add(CachedVariant(i, variantChain))
                            }
                        }
                    }
                }

                // If we have a result at this depth, don't bother building the next depth's states.
                if (!results.isEmpty()) {
                    continue
                }

                // Construct new states for processing at the next depth in case we can't find any solutions at this depth.
                for (i in candidates.indices) {
                    val candidate = candidates.get(i)
                    if (!Collections.disjoint(state.requested.keySet(), candidate.to.keySet())) {
                        nextDepth.add(
                            ChainState(
                                ChainNode(state.chain, candidate),
                                attributesFactory.concat(state.requested, candidate.from),
                                state.transforms.withoutIndexFrom(i, candidates)
                            )
                        )
                    }
                }
            }

            toProcess.clear()
            val tmp = toProcess
            toProcess = nextDepth
            nextDepth = tmp
        }

        return results
    }

    /**
     * Constructs a complete cacheable variant chain given a root transformed variant and the chain of variants
     * to apply to that root variant.
     *
     * @param stateChain The transform chain from the search state to apply to the root transformed variant.
     * @param root The root variant to apply the chain to.
     *
     * @return A variant chain representing the final transformed variant.
     */
    private fun createVariantChain(stateChain: ChainNode, root: DefaultVariantDefinition): VariantDefinition {
        var node: ChainNode? = stateChain
        var last = root
        while (node != null) {
            last = DefaultVariantDefinition(
                last,
                attributesFactory.concat(last.getTargetAttributes(), node.transform.to),
                node.transform.transformStep
            )
            node = node.next
        }
        return last
    }

    /**
     * Caches calls to the transform chain selection algorithm. The cached results are stored in
     * a variant-independent manner, such that only the attributes of the input variants are cached.
     * This way, if multiple calls are made with different variants but those variants have the same
     * attributes, the cached results may be used.
     */
    private class TransformCache(private val action: BiFunction<MutableList<ImmutableAttributes>, ImmutableAttributes, MutableList<CachedVariant>>) {
        private val cache = ConcurrentHashMap<CacheKey, MutableList<CachedVariant>>()

        fun query(
            sources: MutableList<ResolvedVariant>, requested: ImmutableAttributes
        ): MutableList<TransformedVariant> {
            val variantAttributes: MutableList<ImmutableAttributes> = ArrayList<ImmutableAttributes>(sources.size)
            for (variant in sources) {
                variantAttributes.add(variant.attributes)
            }
            val cached = cache.computeIfAbsent(CacheKey(variantAttributes, requested)) { key: CacheKey? -> action.apply(key.variantAttributes, key.requested) }
            val output: MutableList<TransformedVariant> = ArrayList<TransformedVariant>(cached.size)
            for (variant in cached) {
                output.add(TransformedVariant(sources.get(variant.sourceIndex), variant.chain))
            }
            return output
        }

        private class CacheKey(private val variantAttributes: MutableList<ImmutableAttributes>, private val requested: ImmutableAttributes) {
            private val hashCode: Int

            init {
                this.hashCode = 31 * variantAttributes.hashCode() + requested.hashCode()
            }

            override fun equals(o: Any): Boolean {
                if (this === o) {
                    return true
                }
                if (o == null || javaClass != o.javaClass) {
                    return false
                }
                val cacheKey = o as CacheKey
                return variantAttributes == cacheKey.variantAttributes && requested == cacheKey.requested
            }

            override fun hashCode(): Int {
                return hashCode
            }
        }
    }
}
