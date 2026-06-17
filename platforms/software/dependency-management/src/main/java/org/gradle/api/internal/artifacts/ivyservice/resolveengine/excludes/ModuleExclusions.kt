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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.CachingExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.LoggingExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.NormalizingExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.OptimizingExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap

@ServiceScope(Scope.Build::class)
class ModuleExclusions {
    private val mergeCaches = CachingExcludeFactory.MergeCaches()

    // please keep the formatting below as it helps enabling or disabling stages
    private val factory: ExcludeFactory = OptimizingExcludeFactory( // optimizes for nulls, 2-params, ... mandatory
        CachingExcludeFactory( // caches the result of TL operations
            LoggingExcludeFactory.Companion.maybeLog(
                NormalizingExcludeFactory( // performs algebra
                    CachingExcludeFactory( // caches the result of optimization operations
                        DefaultExcludeFactory(),  // the end of the chain, mandatory
                        mergeCaches // shares the same caches as the top level one as after reducing we can find already cached merge operations
                    )
                )
            ),
            mergeCaches
        )
    )
    private val metadataToExcludeCache: MutableMap<ExcludeMetadata?, ExcludeSpec?> = ConcurrentHashMap<ExcludeMetadata?, ExcludeSpec?>()
    private val nothing: ExcludeSpec?

    init {
        nothing = factory.nothing()
    }

    fun excludeAny(excludes: MutableCollection<out ExcludeMetadata?>): ExcludeSpec? {
        if (excludes.isEmpty()) {
            return nothing
        }
        if (excludes.size == 1) {
            return forExclude(excludes.iterator().next())
        }
        var result = PersistentSet.of<ExcludeSpec?>()
        for (exclude in excludes) {
            result = result.plus(forExclude(exclude))
        }
        return factory.anyOf(result)
    }

    fun nothing(): ExcludeSpec? {
        return nothing
    }

    private fun forExclude(r: ExcludeMetadata?): ExcludeSpec? {
        return metadataToExcludeCache.computeIfAbsent(r) { rule: ExcludeMetadata? ->
            // For custom ivy pattern matchers, don't inspect the rule any more deeply: this prevents us from doing smart merging later
            if (!PatternMatchers.Companion.isExactMatcher(rule!!.matcher)) {
                return@computeIfAbsent factory.ivyPatternExclude(rule.moduleId, rule.artifact, rule.matcher)
            }

            val moduleId: ModuleIdentifier = rule.moduleId
            val artifact = rule.artifact
            val anyOrganisation: Boolean = isWildcard(moduleId.getGroup())
            val anyModule: Boolean = isWildcard(moduleId.getName())

            // Build a strongly typed (mergeable) exclude spec for each supplied rule
            if (artifact == null) {
                if (!anyOrganisation && !anyModule) {
                    return@computeIfAbsent factory.moduleId(moduleId)
                } else if (!anyModule) {
                    return@computeIfAbsent factory.module(moduleId.getName())
                } else if (!anyOrganisation) {
                    return@computeIfAbsent factory.group(moduleId.getGroup())
                } else {
                    return@computeIfAbsent factory.everything()
                }
            } else {
                return@computeIfAbsent factory.ivyPatternExclude(moduleId, artifact, rule.matcher)
            }
        }
    }

    fun excludeAny(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return factory.anyOf(one, two)
    }

    fun excludeAll(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return factory.allOf(one, two)
    }

    fun excludeAll(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return factory.allOf(specs)
    }

    fun excludeAny(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return factory.anyOf(specs)
    }

    companion object {
        private fun isWildcard(attribute: String?): Boolean {
            return PatternMatchers.Companion.ANY_EXPRESSION == attribute
        }
    }
}
