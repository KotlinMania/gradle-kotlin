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
package org.gradle.api.internal.artifacts.ivyservice

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl
import org.gradle.api.internal.artifacts.cache.ResolutionControl
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Default implementation of [CacheExpirationControl].
 */
class DefaultCacheExpirationControl(
    private val dependencyCacheRules: ImmutableList<Action<in DependencyResolutionControl?>>,
    private val moduleCacheRules: ImmutableList<Action<in ModuleResolutionControl?>>,
    private val artifactCacheRules: ImmutableList<Action<in ArtifactResolutionControl?>>,
    private val keepDynamicVersionsFor: Long,
    private val keepChangingModulesFor: Long,
    private val offline: Boolean,
    private val refresh: Boolean
) : CacheExpirationControl {
    override fun versionListExpiry(moduleIdentifier: ModuleIdentifier?, moduleVersions: MutableSet<ModuleVersionIdentifier?>?, age: Duration): CacheExpirationControl.Expiry {
        val dependencyResolutionControl = CachedDependencyResolutionControl(moduleIdentifier, moduleVersions, age.toMillis(), keepDynamicVersionsFor)

        if (applyOfflineRule(dependencyResolutionControl) || applyRefreshRule(dependencyResolutionControl)) {
            return dependencyResolutionControl
        }

        for (rule in dependencyCacheRules) {
            rule.execute(dependencyResolutionControl)
            if (dependencyResolutionControl.ruleMatch()) {
                break
            }
        }

        return dependencyResolutionControl
    }

    override fun missingModuleExpiry(component: ModuleComponentIdentifier, age: Duration?): CacheExpirationControl.Expiry? {
        return mustRefreshModule(component, null, age, false)
    }

    override fun moduleExpiry(component: ModuleComponentIdentifier, resolvedModuleVersion: ResolvedModuleVersion?, age: Duration?): CacheExpirationControl.Expiry? {
        return mustRefreshModule(component, resolvedModuleVersion, age, false)
    }

    override fun moduleExpiry(resolvedModuleVersion: ResolvedModuleVersion, age: Duration, changing: Boolean): CacheExpirationControl.Expiry {
        return mustRefreshModule(resolvedModuleVersion.getId(), resolvedModuleVersion, age, changing)
    }

    override fun moduleArtifactsExpiry(
        moduleVersionId: ModuleVersionIdentifier?, artifacts: MutableSet<ModuleComponentArtifactMetadata?>?,
        age: Duration, belongsToChangingModule: Boolean, moduleDescriptorInSync: Boolean
    ): CacheExpirationControl.Expiry {
        val resolutionControl = mustRefreshModule(moduleVersionId, DefaultResolvedModuleVersion(moduleVersionId), age, belongsToChangingModule)
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            resolutionControl.refresh()
        }
        return resolutionControl
    }

    override fun artifactExpiry(
        artifactMetadata: ModuleComponentArtifactMetadata?,
        cachedArtifactFile: File?,
        age: Duration,
        belongsToChangingModule: Boolean,
        moduleDescriptorInSync: Boolean
    ): CacheExpirationControl.Expiry {
        val artifactResolutionControl = CachedArtifactResolutionControl(artifactMetadata, cachedArtifactFile, age.toMillis(), keepChangingModulesFor, belongsToChangingModule)

        if (applyOfflineRule(artifactResolutionControl) || applyRefreshRule(artifactResolutionControl)) {
            return artifactResolutionControl
        }

        for (rule in artifactCacheRules) {
            rule.execute(artifactResolutionControl)
            if (artifactResolutionControl.ruleMatch()) {
                break
            }
        }

        if (belongsToChangingModule && !moduleDescriptorInSync) {
            artifactResolutionControl.refresh()
        }

        return artifactResolutionControl
    }

    override fun changingModuleExpiry(component: ModuleComponentIdentifier, resolvedModuleVersion: ResolvedModuleVersion?, age: Duration?): CacheExpirationControl.Expiry? {
        return mustRefreshModule(component, resolvedModuleVersion, age, true)
    }

    private fun mustRefreshModule(component: ModuleComponentIdentifier, version: ResolvedModuleVersion?, age: Duration?, changingModule: Boolean): CacheExpirationControl.Expiry? {
        return mustRefreshModule(DefaultModuleVersionIdentifier.newId(component.getModuleIdentifier(), component.getVersion()), version, age!!, changingModule)
    }

    private fun mustRefreshModule(moduleVersionId: ModuleVersionIdentifier?, version: ResolvedModuleVersion?, age: Duration, changingModule: Boolean): CachedModuleResolutionControl {
        val moduleResolutionControl = CachedModuleResolutionControl(moduleVersionId, version, changingModule, age.toMillis(), if (changingModule) keepChangingModulesFor else Long.MAX_VALUE)

        if (applyOfflineRule(moduleResolutionControl) || applyRefreshRule(moduleResolutionControl)) {
            return moduleResolutionControl
        }

        for (rule in moduleCacheRules) {
            rule.execute(moduleResolutionControl)
            if (moduleResolutionControl.ruleMatch()) {
                break
            }
        }

        return moduleResolutionControl
    }

    /**
     * @param resolutionControl The resolution control to mutate
     * @return If the offline rule was applied
     */
    private fun applyOfflineRule(resolutionControl: ResolutionControl<*, *>): Boolean {
        if (offline) {
            resolutionControl.useCachedResult()
            return true
        }
        return false
    }

    /**
     * @param resolutionControl The resolution control to mutate
     * @return If the refresh rule was applied
     */
    private fun applyRefreshRule(resolutionControl: ResolutionControl<*, *>): Boolean {
        if (refresh) {
            resolutionControl.refresh()
            return true
        }
        return false
    }

    private abstract class AbstractResolutionControl<A, B>(private val request: A?, private val cachedResult: B?, ageMillis: Long, private var keepForMillis: Long) : ResolutionControl<A?, B?>,
        CacheExpirationControl.Expiry {
        private val ageMillis: Long
        private var ruleMatch = false
        private var mustCheck = false

        init {
            this.ageMillis = correctForClockShift(ageMillis)
        }

        override fun getRequest(): A? {
            return request
        }

        override fun getCachedResult(): B? {
            return cachedResult
        }

        override fun cacheFor(value: Int, units: TimeUnit) {
            keepForMillis = TimeUnit.MILLISECONDS.convert(value.toLong(), units)
            setMustCheck(ageMillis > keepForMillis)
        }

        override fun useCachedResult() {
            setMustCheck(false)
        }

        override fun refresh() {
            setMustCheck(true)
        }

        fun setMustCheck(`val`: Boolean) {
            ruleMatch = true
            mustCheck = `val`
        }

        fun ruleMatch(): Boolean {
            return ruleMatch
        }

        override fun getKeepFor(): Duration? {
            if (mustCheck && ageMillis > 0) {
                // Must check and was not cached in this build, so do not keep the value
                return Duration.ZERO
            }
            if (keepForMillis == Long.MAX_VALUE) {
                return Duration.ofMillis(Long.MAX_VALUE)
            }
            return Duration.ofMillis(max(0, keepForMillis - ageMillis))
        }

        override fun isMustCheck(): Boolean {
            return mustCheck && ageMillis > 0
        }

        companion object {
            /**
             * If the age is less than 0, then it's probable that we've had a clock shift. In this case, treat the age as 1ms.
             */
            private fun correctForClockShift(ageMillis: Long): Long {
                if (ageMillis < 0) {
                    return 1
                }
                return ageMillis
            }
        }
    }

    private class CachedModuleResolutionControl(moduleVersionId: ModuleVersionIdentifier?, cachedVersion: ResolvedModuleVersion?, private val changing: Boolean, ageMillis: Long, keepForMillis: Long) :
        AbstractResolutionControl<ModuleVersionIdentifier?, ResolvedModuleVersion?>(moduleVersionId, cachedVersion, ageMillis, keepForMillis), ModuleResolutionControl {
        override fun isChanging(): Boolean {
            return changing
        }
    }

    private class CachedArtifactResolutionControl(artifact: ModuleComponentArtifactMetadata?, cachedResult: File?, ageMillis: Long, keepForMillis: Long, private val belongsToChangingModule: Boolean) :
        AbstractResolutionControl<ModuleComponentArtifactMetadata?, File?>(artifact, cachedResult, ageMillis, keepForMillis), ArtifactResolutionControl {
        override fun belongsToChangingModule(): Boolean {
            return belongsToChangingModule
        }
    }

    private class CachedDependencyResolutionControl(request: ModuleIdentifier?, result: MutableSet<ModuleVersionIdentifier?>?, ageMillis: Long, keepForMillis: Long) :
        AbstractResolutionControl<ModuleIdentifier?, MutableSet<ModuleVersionIdentifier?>?>(request, result, ageMillis, keepForMillis), DependencyResolutionControl
}
