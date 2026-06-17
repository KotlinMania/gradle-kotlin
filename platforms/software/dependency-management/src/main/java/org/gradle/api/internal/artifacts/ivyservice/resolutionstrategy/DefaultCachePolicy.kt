/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl
import org.gradle.api.internal.artifacts.configurations.CachePolicy
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheExpirationControl
import java.util.concurrent.TimeUnit

class DefaultCachePolicy : CachePolicy {
    val dependencyCacheRules: MutableList<Action<in DependencyResolutionControl>>
    val moduleCacheRules: MutableList<Action<in ModuleResolutionControl>>
    val artifactCacheRules: MutableList<Action<in ArtifactResolutionControl>>
    private var mutationValidator = MutationValidator.IGNORE
    private var keepDynamicVersionsFor = MILLISECONDS_IN_DAY.toLong()
    private var keepChangingModulesFor = MILLISECONDS_IN_DAY.toLong()
    private var offline = false
    private var refresh = false

    constructor() {
        this.dependencyCacheRules = ArrayList<Action<in DependencyResolutionControl>>(1)
        this.moduleCacheRules = ArrayList<Action<in ModuleResolutionControl>>(1)
        this.artifactCacheRules = ArrayList<Action<in ArtifactResolutionControl>>(2)

        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS)
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS)
        cacheMissingArtifactsFor(SECONDS_IN_DAY, TimeUnit.SECONDS)
    }

    private constructor(policy: DefaultCachePolicy) {
        this.dependencyCacheRules = ArrayList<Action<in DependencyResolutionControl>>(policy.dependencyCacheRules)
        this.moduleCacheRules = ArrayList<Action<in ModuleResolutionControl>>(policy.moduleCacheRules)
        this.artifactCacheRules = ArrayList<Action<in ArtifactResolutionControl>>(policy.artifactCacheRules)
        this.keepDynamicVersionsFor = policy.keepDynamicVersionsFor
        this.keepChangingModulesFor = policy.keepChangingModulesFor
        this.offline = policy.offline
        this.refresh = policy.refresh
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
    override fun setMutationValidator(validator: MutationValidator) {
        this.mutationValidator = validator
    }

    override fun setOffline() {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        offline = true
    }

    override fun setRefreshDependencies() {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        refresh = true
    }

    override fun cacheDynamicVersionsFor(value: Int, unit: TimeUnit) {
        keepDynamicVersionsFor = unit.toMillis(value.toLong())
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        dependencyCacheRules.add(0, Action { dependencyResolutionControl: DependencyResolutionControl? ->
            if (!dependencyResolutionControl!!.cachedResult!!.isEmpty()) {
                dependencyResolutionControl.cacheFor(value, unit)
            }
        })
    }

    override fun cacheChangingModulesFor(value: Int, units: TimeUnit) {
        keepChangingModulesFor = units.toMillis(value.toLong())
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)

        moduleCacheRules.add(0, Action { moduleResolutionControl: ModuleResolutionControl? ->
            if (moduleResolutionControl!!.isChanging) {
                moduleResolutionControl.cacheFor(value, units)
            }
        })

        artifactCacheRules.add(0, Action { artifactResolutionControl: ArtifactResolutionControl? ->
            if (artifactResolutionControl!!.belongsToChangingModule()) {
                artifactResolutionControl.cacheFor(value, units)
            }
        })
    }

    private fun cacheMissingArtifactsFor(value: Int, units: TimeUnit) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        artifactCacheRules.add(0, Action { artifactResolutionControl: ArtifactResolutionControl? ->
            if (artifactResolutionControl!!.cachedResult == null) {
                artifactResolutionControl.cacheFor(value, units)
            }
        })
    }

    override fun copy(): CachePolicy {
        return DefaultCachePolicy(this)
    }

    override fun asImmutable(): CacheExpirationControl {
        return DefaultCacheExpirationControl(
            ImmutableList.copyOf<Action<in DependencyResolutionControl>>(dependencyCacheRules),
            ImmutableList.copyOf<Action<in ModuleResolutionControl>>(moduleCacheRules),
            ImmutableList.copyOf<Action<in ArtifactResolutionControl>>(artifactCacheRules),
            keepDynamicVersionsFor,
            keepChangingModulesFor,
            offline,
            refresh
        )
    }

    companion object {
        private val SECONDS_IN_DAY = 24 * 60 * 60
        private val MILLISECONDS_IN_DAY: Int = SECONDS_IN_DAY * 1000
    }
}
