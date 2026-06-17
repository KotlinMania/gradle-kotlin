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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.util.internal.GUtil
import java.io.Serializable

// does not override equals() but hashCode() in order to cache the latter's
// pre-computed value to improve performance when used in HashMaps
class DefaultImmutableVersionConstraint : AbstractVersionConstraint, ImmutableVersionConstraint, Serializable {
    private val requiredVersion: String
    private val preferredVersion: String
    private val strictVersion: String
    private val rejectedVersions: ImmutableList<String>
    private val requiredBranch: String?

    private val hashCode: Int

    constructor(
        preferredVersion: String,
        requiredVersion: String,
        strictVersion: String,
        rejectedVersions: MutableList<String>,
        requiredBranch: String?
    ) {
        requireNotNull(preferredVersion) { "Preferred version must not be null" }
        requireNotNull(requiredVersion) { "Required version must not be null" }
        requireNotNull(strictVersion) { "Strict version must not be null" }
        requireNotNull(rejectedVersions) { "Rejected versions must not be null" }
        for (rejectedVersion in rejectedVersions) {
            require(GUtil.isTrue(rejectedVersion)) { "Rejected version must not be empty" }
        }
        this.preferredVersion = preferredVersion
        this.requiredVersion = requiredVersion
        this.strictVersion = strictVersion
        this.rejectedVersions = ImmutableList.copyOf<String>(rejectedVersions)
        this.requiredBranch = requiredBranch
        this.hashCode = super.hashCode()
    }

    constructor(requiredVersion: String) {
        requireNotNull(requiredVersion) { "Required version must not be null" }
        this.preferredVersion = ""
        this.requiredVersion = requiredVersion
        this.strictVersion = ""
        this.rejectedVersions = ImmutableList.of<String>()
        this.requiredBranch = null
        this.hashCode = super.hashCode()
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun getBranch(): String? {
        return requiredBranch
    }

    override fun getRequiredVersion(): String {
        return requiredVersion
    }

    override fun getPreferredVersion(): String {
        return preferredVersion
    }

    override fun getStrictVersion(): String {
        return strictVersion
    }

    override fun getRejectedVersions(): MutableList<String> {
        return rejectedVersions
    }

    companion object {
        private val EMPTY = DefaultImmutableVersionConstraint("")
        @JvmStatic
        fun of(versionConstraint: VersionConstraint): ImmutableVersionConstraint {
            if (versionConstraint is ImmutableVersionConstraint) {
                return versionConstraint
            }
            return DefaultImmutableVersionConstraint(
                versionConstraint.getPreferredVersion(),
                versionConstraint.getRequiredVersion(),
                versionConstraint.getStrictVersion(),
                versionConstraint.getRejectedVersions(),
                versionConstraint.getBranch()
            )
        }

        @JvmStatic
        fun of(version: String?): ImmutableVersionConstraint {
            if (version == null) {
                return of()
            }
            return DefaultImmutableVersionConstraint(version)
        }

        fun of(preferredVersion: String, requiredVersion: String, strictVersion: String, rejects: MutableList<String>): ImmutableVersionConstraint {
            return DefaultImmutableVersionConstraint(preferredVersion, requiredVersion, strictVersion, rejects, null)
        }

        @JvmStatic
        fun of(): ImmutableVersionConstraint {
            return EMPTY
        }

        fun strictly(version: String): ImmutableVersionConstraint {
            return DefaultImmutableVersionConstraint("", version, version, ImmutableList.of<String>(), null)
        }
    }
}
