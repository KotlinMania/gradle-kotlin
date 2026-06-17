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

import com.google.common.base.Strings
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.VersionConstraintInternal
import java.util.Collections

class DefaultMutableVersionConstraint private constructor(
    preferredVersion: String?,
    requiredVersion: String,
    strictVersion: String?,
    rejects: MutableList<String> = mutableListOf<String>(),
    branch: String? = null
) : AbstractVersionConstraint(), VersionConstraintInternal {
    private var requiredVersion: String? = null
    private var preferredVersion: String? = null
    private var strictVersion: String? = null
    private var branch: String? = null
    private val rejectedVersions: MutableList<String> = ArrayList<String>(1)

    constructor(versionConstraint: VersionConstraint) : this(
        versionConstraint.getPreferredVersion(),
        versionConstraint.getRequiredVersion(),
        versionConstraint.getStrictVersion(),
        versionConstraint.getRejectedVersions(),
        versionConstraint.getBranch()
    )

    constructor(version: String) : this(null, version, null)

    init {
        updateVersions(preferredVersion, requiredVersion, strictVersion)
        for (reject in rejects) {
            this.rejectedVersions.add(Strings.nullToEmpty(reject))
        }
        this.branch = branch
    }

    private fun updateVersions(preferredVersion: String?, requiredVersion: String?, strictVersion: String?) {
        this.preferredVersion = Strings.nullToEmpty(preferredVersion)
        this.requiredVersion = Strings.nullToEmpty(requiredVersion)
        this.strictVersion = Strings.nullToEmpty(strictVersion)
        this.rejectedVersions.clear()
    }

    override fun asImmutable(): ImmutableVersionConstraint {
        return DefaultImmutableVersionConstraint(preferredVersion!!, requiredVersion!!, strictVersion!!, rejectedVersions, branch)
    }

    override fun getBranch(): String? {
        return branch
    }

    override fun setBranch(branch: String?) {
        this.branch = branch
    }

    override fun getRequiredVersion(): String {
        return requiredVersion!!
    }

    override fun require(version: String) {
        updateVersions(preferredVersion, version, null)
    }

    override fun getPreferredVersion(): String {
        return preferredVersion!!
    }

    override fun prefer(version: String) {
        updateVersions(version, requiredVersion, strictVersion)
    }

    override fun getStrictVersion(): String {
        return strictVersion!!
    }

    override fun strictly(version: String) {
        updateVersions(preferredVersion, version, version)
    }

    override fun reject(vararg versions: String) {
        this.rejectedVersions.clear()
        Collections.addAll<String>(rejectedVersions, *versions)
    }

    override fun rejectAll() {
        updateVersions(null, null, null)
        this.rejectedVersions.add("+")
    }

    override fun getRejectedVersions(): MutableList<String> {
        return rejectedVersions
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }

        val that = o as DefaultMutableVersionConstraint

        if (if (requiredVersion != null) (requiredVersion != that.requiredVersion) else that.requiredVersion != null) {
            return false
        }
        if (if (preferredVersion != null) (preferredVersion != that.preferredVersion) else that.preferredVersion != null) {
            return false
        }
        if (if (strictVersion != null) (strictVersion != that.strictVersion) else that.strictVersion != null) {
            return false
        }
        if (if (branch != null) (branch != that.branch) else that.branch != null) {
            return false
        }
        return rejectedVersions == that.rejectedVersions
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (if (requiredVersion != null) requiredVersion.hashCode() else 0)
        result = 31 * result + (if (preferredVersion != null) preferredVersion.hashCode() else 0)
        result = 31 * result + (if (strictVersion != null) strictVersion.hashCode() else 0)
        result = 31 * result + (if (branch != null) branch.hashCode() else 0)
        result = 31 * result + rejectedVersions.hashCode()
        return result
    }

    companion object {
        fun withVersion(version: String): DefaultMutableVersionConstraint {
            return DefaultMutableVersionConstraint(version)
        }

        fun withStrictVersion(version: String): DefaultMutableVersionConstraint {
            return DefaultMutableVersionConstraint(null, version, version)
        }
    }
}
