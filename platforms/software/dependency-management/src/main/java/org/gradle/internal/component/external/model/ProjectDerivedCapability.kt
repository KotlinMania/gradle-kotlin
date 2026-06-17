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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import org.gradle.api.InvalidUserDataException
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.internal.TextUtil
import kotlin.concurrent.Volatile

class ProjectDerivedCapability @JvmOverloads constructor(private val project: ProjectInternal, private val featureName: String? = null) : CapabilityInternal {
    @Volatile
    private var capabilityName: String? = null

    override fun getGroup(): String {
        return notNull("group", project.getGroup())
    }

    override fun getName(): String {
        if (capabilityName == null) {
            capabilityName = computeCapabilityName(project, featureName)
        }
        return capabilityName!!
    }

    override fun getVersion(): String {
        return notNull("version", project.getVersion())
    }

    override fun hashCode(): Int {
        // See DefaultImmutableCapability#computeHashcode
        var hash = getVersion().hashCode()
        hash = 31 * hash + getName().hashCode()
        hash = 31 * hash + getGroup().hashCode()
        return hash
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Capability) {
            return false
        }

        val that = o
        return Objects.equal(getGroup(), that.getGroup())
                && Objects.equal(getName(), that.getName())
                && Objects.equal(getVersion(), that.getVersion())
    }

    override fun getCapabilityId(): String {
        return getGroup() + ":" + getName()
    }

    companion object {
        private fun computeCapabilityName(project: ProjectInternal, featureName: String?): String {
            val projectName = project.getOwner().getIdentity().getProjectName()
            if (featureName == null) {
                return projectName
            }
            return projectName + "-" + TextUtil.camelToKebabCase(featureName)
        }

        private fun notNull(id: String, o: Any): String {
            if (o == null) {
                throw InvalidUserDataException(id + " must not be null")
            }
            return o.toString()
        }
    }
}
