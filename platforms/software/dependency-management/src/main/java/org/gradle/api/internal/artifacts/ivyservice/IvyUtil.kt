/*
 * Copyright 2009 the original author or authors.
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

import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.util.internal.GUtil

object IvyUtil {
    private val MODULE_ID_LOCK = Any() //see GRADLE-3027

    fun createModuleRevisionId(group: String?, name: String, version: String?): ModuleRevisionId? {
        return createModuleRevisionId(emptyStringIfNull(group), name, null, emptyStringIfNull(version), mutableMapOf<String?, String?>())
    }

    fun createModuleRevisionId(id: ModuleComponentIdentifier): ModuleRevisionId? {
        return createModuleRevisionId(id.getGroup(), id.getModule(), id.getVersion())
    }

    private fun emptyStringIfNull(value: String?): String? {
        return GUtil.elvis<String?>(value, "")
    }

    fun createModuleRevisionId(org: String?, name: String, branch: String?, rev: String?, extraAttributes: MutableMap<String?, String?>?): ModuleRevisionId? {
        return org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleRevisionId(org, name, branch, rev, extraAttributes, true)
    }

    fun createModuleRevisionId(
        org: String?,
        name: String,
        branch: String?,
        revConstraint: String?,
        extraAttributes: MutableMap<String?, String?>?,
        replaceNullBranchWithDefault: Boolean
    ): ModuleRevisionId? {
        synchronized(org.gradle.api.internal.artifacts.ivyservice.IvyUtil.MODULE_ID_LOCK) {
            return org.apache.ivy.core.module.id.ModuleRevisionId.newInstance(org, name, branch, revConstraint, extraAttributes, replaceNullBranchWithDefault)
        }
    }

    fun createModuleId(org: String?, name: String): ModuleId? {
        synchronized(org.gradle.api.internal.artifacts.ivyservice.IvyUtil.MODULE_ID_LOCK) {
            return org.apache.ivy.core.module.id.ModuleId.newInstance(org, name)
        }
    }
}
