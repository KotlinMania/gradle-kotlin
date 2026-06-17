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
package org.gradle.api.internal.artifacts.dependencies

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme

class DefaultResolvedVersionConstraint @VisibleForTesting constructor(
    requiredVersion: String,
    preferredVersion: String,
    strictVersion: String,
    rejectedVersions: MutableList<String>,
    scheme: VersionSelectorScheme
) : ResolvedVersionConstraint {
    val preferredSelector: VersionSelector?
    val requiredSelector: VersionSelector
    val rejectedSelector: VersionSelector
    val isStrict: Boolean
    val isRejectAll: Boolean
    val isDynamic: Boolean

    constructor(parent: VersionConstraint, scheme: VersionSelectorScheme) : this(
        parent.getRequiredVersion(),
        parent.getPreferredVersion(),
        parent.getStrictVersion(),
        parent.getRejectedVersions(),
        scheme
    )

    init {
        // For now, required and preferred are treated the same

        isStrict = !strictVersion.isEmpty()
        val version = if (isStrict) strictVersion else requiredVersion
        this.requiredSelector = scheme.parseSelector(version)!!
        this.preferredSelector = if (preferredVersion.isEmpty()) null else scheme.parseSelector(preferredVersion)

        if (isStrict) {
            val rejectionForStrict: VersionSelector = getRejectionForStrict(version, scheme)
            if (!rejectedVersions.isEmpty()) {
                val explicitRejected: VersionSelector = toRejectSelector(scheme, rejectedVersions)
                this.rejectedSelector = UnionVersionSelector(ImmutableList.of<VersionSelector>(rejectionForStrict, explicitRejected))
            } else {
                this.rejectedSelector = rejectionForStrict
            }
            this.isRejectAll = false
        } else {
            this.rejectedSelector = toRejectSelector(scheme, rejectedVersions)
            this.isRejectAll = isRejectAll(version, rejectedVersions)
        }
        this.isDynamic = doComputeIsDynamic()
    }

    override fun accepts(candidate: String): Boolean {
        var accepted = true
        if (this.requiredSelector != null) {
            accepted = requiredSelector.accept(candidate)
        } else if (this.preferredSelector != null) {
            accepted = preferredSelector.accept(candidate)
        }
        if (accepted && this.rejectedSelector != null) {
            accepted = !rejectedSelector.accept(candidate)
        }
        return accepted
    }

    override fun canBeStable(): Boolean {
        return Companion.canBeStable(this.preferredSelector!!)
                && canBeStable(this.requiredSelector)
                && canBeStable(this.rejectedSelector)
    }

    private fun doComputeIsDynamic(): Boolean {
        if (this.requiredSelector != null) {
            return requiredSelector.isDynamic
        }
        if (this.preferredSelector != null) {
            return preferredSelector.isDynamic
        }
        return false
    }

    companion object {
        private fun getRejectionForStrict(version: String, versionSelectorScheme: VersionSelectorScheme): VersionSelector {
            val preferredSelector: VersionSelector = versionSelectorScheme.parseSelector(version)!!
            return versionSelectorScheme.complementForRejection(preferredSelector)!!
        }

        private fun toRejectSelector(scheme: VersionSelectorScheme, rejectedVersions: MutableList<String>): VersionSelector {
            if (rejectedVersions.size > 1) {
                return UnionVersionSelector.of(rejectedVersions, scheme)
            }
            return (if (rejectedVersions.isEmpty()) null else scheme.parseSelector(rejectedVersions.get(0)))!!
        }

        private fun canBeStable(vs: VersionSelector): Boolean {
            if (vs == null) {
                return true
            }
            return vs.canShortCircuitWhenVersionAlreadyPreselected()
        }

        private fun isRejectAll(preferredVersion: String, rejectedVersions: MutableList<String>): Boolean {
            return "" == preferredVersion
                    && hasMatchAllSelector(rejectedVersions)
        }

        private fun hasMatchAllSelector(rejectedVersions: MutableList<String>): Boolean {
            for (version in rejectedVersions) {
                if ("+" == version) {
                    return true
                }
            }
            return false
        }
    }
}
