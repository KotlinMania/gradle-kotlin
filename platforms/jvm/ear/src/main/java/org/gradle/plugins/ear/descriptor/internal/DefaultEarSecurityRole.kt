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
package org.gradle.plugins.ear.descriptor.internal

import com.google.common.base.Objects
import org.gradle.plugins.ear.descriptor.EarSecurityRole
import javax.inject.Inject

class DefaultEarSecurityRole : EarSecurityRole {
    private var description: String? = null
    private var roleName: String? = null

    @Inject
    constructor()

    constructor(roleName: String?) {
        this.roleName = roleName
    }

    constructor(roleName: String?, description: String?) {
        this.roleName = roleName
        this.description = description
    }

    override fun getDescription(): String? {
        return description
    }

    override fun setDescription(description: String?) {
        this.description = description
    }

    override fun getRoleName(): String? {
        return roleName
    }

    override fun setRoleName(roleName: String?) {
        this.roleName = roleName
    }

    override fun hashCode(): Int {
        var result: Int
        result = if (description != null) description.hashCode() else 0
        result = 31 * result + (if (roleName != null) roleName.hashCode() else 0)
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is DefaultEarSecurityRole) {
            return false
        }
        val that = o
        return Objects.equal(description, that.description) && Objects.equal(roleName, that.roleName)
    }
}
