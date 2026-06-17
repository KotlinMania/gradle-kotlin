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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyArtifactSelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.internal.Try
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.instantiation.InstanceFactory
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.InMemoryLoadingCache
import java.util.function.Function

/**
 * Default implementation of [DependencySubstitutionApplicator], which caches results of
 * executing substitution rules.
 */
class DefaultDependencySubstitutionApplicator(
    componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
    rule: Action<in DependencySubstitutionInternal>,
    instantiatorFactory: InstantiatorFactory,
    cacheFactory: InMemoryCacheFactory
) : DependencySubstitutionApplicator {
    private val cache: InMemoryLoadingCache<SubstitutionCacheKey, Try<SubstitutionResult>>

    init {
        val substitutionFactory =
            instantiatorFactory.decorateScheme().forType<DefaultDependencySubstitution>(DefaultDependencySubstitution::class.java)

        this.cache = cacheFactory.create<SubstitutionCacheKey, Try<SubstitutionResult?>>(Function { key: SubstitutionCacheKey ->
            Try.ofFailable({
                executeSubstitutionRule(
                    substitutionFactory,
                    componentSelectionDescriptorFactory,
                    key,
                    rule
                )
            })
        })
    }

    override fun applySubstitutions(selector: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): Try<SubstitutionResult?> {
        return cache.get(SubstitutionCacheKey(selector, artifacts))
    }

    /**
     * Represents all information from a dependency metadata required for executing substitution rules.
     */
    private class SubstitutionCacheKey(private val target: ComponentSelector, private val artifacts: ImmutableList<IvyArtifactName>) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(
                artifacts,
                target
            )
        }

        override fun equals(o: Any): Boolean {
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as SubstitutionCacheKey
            return target == that.target &&
                    artifacts == that.artifacts
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(artifacts: MutableList<IvyArtifactName>, selector: ComponentSelector): Int {
                var result = selector.hashCode()
                result = 31 * result + artifacts.hashCode()
                return result
            }
        }
    }

    class DefaultSubstitutionResult(
        private val target: ComponentSelector?,
        private val artifacts: ImmutableList<IvyArtifactName>?,
        private val ruleDescriptors: ImmutableList<ComponentSelectionDescriptorInternal>
    ) : SubstitutionResult {
        override fun getTarget(): ComponentSelector? {
            return target
        }

        override fun getArtifacts(): ImmutableList<IvyArtifactName>? {
            return artifacts
        }

        override fun getRuleDescriptors(): ImmutableList<ComponentSelectionDescriptorInternal> {
            return ruleDescriptors
        }

        companion object {
            val NO_OP: SubstitutionResult = DefaultSubstitutionResult(null, null, ImmutableList.of<ComponentSelectionDescriptorInternal>())

            /**
             * Given a substitution details that has been configured by the user action, creates a
             * substitution result representing the configured results of the action.
             */
            private fun of(requested: SubstitutionCacheKey, details: DependencySubstitutionInternal): SubstitutionResult {
                val target: ComponentSelector = details.configuredTargetSelector
                val artifacts: ImmutableList<DependencyArtifactSelector>? = details.configuredArtifactSelectors

                if (target == null && artifacts == null) {
                    return NO_OP
                }

                val descriptors: ImmutableList<ComponentSelectionDescriptorInternal> = details.ruleDescriptors
                assert(descriptors != null && !descriptors.isEmpty())

                var artifactNames: ImmutableList<IvyArtifactName>? = null
                if (artifacts != null) {
                    artifactNames = toIvyArtifactNames(target, requested.target, artifacts)
                }

                return DefaultSubstitutionResult(target, artifactNames, descriptors)
            }

            private fun toIvyArtifactNames(
                configuredTarget: ComponentSelector?,
                requestedTarget: ComponentSelector,
                artifacts: ImmutableList<DependencyArtifactSelector>
            ): ImmutableList<IvyArtifactName> {
                if (artifacts.isEmpty()) {
                    return ImmutableList.of<IvyArtifactName>()
                }

                val actualTarget = if (configuredTarget != null) configuredTarget else requestedTarget
                val targetModuleName: String = getModuleName(actualTarget)
                val artifactsBuilder = ImmutableList.builderWithExpectedSize<IvyArtifactName>(artifacts.size)
                for (das in artifacts) {
                    artifactsBuilder.add(
                        DefaultIvyArtifactName(
                            targetModuleName,
                            das.getType(),
                            if (das.getExtension() != null) das.getExtension() else das.getType(),
                            das.getClassifier()
                        )
                    )
                }
                return artifactsBuilder.build()
            }

            private fun getModuleName(target: ComponentSelector): String {
                check(target is ModuleComponentSelector) { "Substitution with artifacts for something else than a module is not supported" }
                return target.getModule()
            }
        }
    }

    companion object {
        private fun executeSubstitutionRule(
            substitutionFactory: InstanceFactory<DefaultDependencySubstitution>,
            componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
            requested: SubstitutionCacheKey,
            rule: Action<in DependencySubstitutionInternal>
        ): SubstitutionResult {
            val details: DependencySubstitutionInternal = substitutionFactory.newInstance(
                componentSelectionDescriptorFactory,
                requested.target,
                requested.artifacts
            )
            rule.execute(details)

            return DefaultSubstitutionResult.Companion.of(requested, details)
        }
    }
}
