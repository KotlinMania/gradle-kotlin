/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.dependency

import com.google.common.base.Strings
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule

class DefaultIvyDependency(
    private val organisation: String?,
    private val module: String?,
    revision: String?,
    private val confMapping: String?,
    private val transitive: Boolean,
    private val revConstraint: String?,
    private val artifacts: MutableSet<DependencyArtifact?>?,
    private val excludeRules: MutableSet<ExcludeRule?>?
) : IvyDependency {
    private val revision: String

    init {
        this.revision = Strings.nullToEmpty(revision)
    }

    override fun getOrganisation(): String? {
        return organisation
    }

    override fun getModule(): String? {
        return module
    }

    override fun getRevision(): String {
        return revision
    }

    override fun getRevConstraint(): String? {
        return revConstraint
    }

    override fun getConfMapping(): String? {
        return confMapping
    }

    override fun isTransitive(): Boolean {
        return transitive
    }

    override fun getArtifacts(): MutableSet<DependencyArtifact?>? {
        return artifacts
    }

    override fun getExcludeRules(): MutableSet<ExcludeRule?>? {
        return excludeRules
    }
}
