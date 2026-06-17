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

import com.google.common.base.Joiner
import com.google.common.base.Objects
import org.gradle.api.artifacts.VersionConstraint

abstract class AbstractVersionConstraint : VersionConstraint {
    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || o.javaClass != javaClass) {
            return false
        }

        val that = o as AbstractVersionConstraint

        return Objects.equal(getRequiredVersion(), that.getRequiredVersion())
                && Objects.equal(getPreferredVersion(), that.getPreferredVersion())
                && Objects.equal(getStrictVersion(), that.getStrictVersion())
                && Objects.equal(getBranch(), that.getBranch())
                && Objects.equal(getRejectedVersions(), that.getRejectedVersions())
    }

    override fun hashCode(): Int {
        return Objects.hashCode(getRequiredVersion(), getPreferredVersion(), getStrictVersion(), getRejectedVersions())
    }

    override fun toString(): String {
        return getDisplayName()
    }

    private fun append(name: String, version: String, builder: StringBuilder) {
        if (version == null || version.isEmpty()) {
            return
        }
        if (builder.length != 1) {
            builder.append("; ")
        }
        builder.append(name)
        builder.append(" ")
        builder.append(version)
    }

    override fun getDisplayName(): String {
        val requiredVersion = getRequiredVersion()
        if (requiredOnly()) {
            return requiredVersion
        }

        val strictVersion = getStrictVersion()
        val preferVersion = getPreferredVersion()
        val builder = StringBuilder()
        builder.append("{")
        append("strictly", strictVersion, builder)
        if (requiredVersion != strictVersion) {
            append("require", requiredVersion, builder)
        }
        if (!(preferVersion == requiredVersion || preferVersion == strictVersion)) {
            append("prefer", getPreferredVersion(), builder)
        }
        append("reject", rejectedVersionsString(), builder)
        append("branch", getBranch()!!, builder)
        builder.append("}")
        return builder.toString()
    }

    private fun requiredOnly(): Boolean {
        return (getPreferredVersion().isEmpty() || getRequiredVersion() == getPreferredVersion())
                && getStrictVersion().isEmpty()
                && getRejectedVersions().isEmpty()
                && getBranch() == null
    }

    private fun rejectedVersionsString(): String {
        val rejectedVersions = getRejectedVersions()
        if (rejectedVersions.size == 1 && rejectedVersions.get(0) == "+") {
            return "all versions"
        } else {
            return Joiner.on(" & ").join(rejectedVersions)
        }
    }
}
