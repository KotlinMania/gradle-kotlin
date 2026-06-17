/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier

/**
 * A limited use, project dependency constraint mostly aimed at publishing
 * platforms.
 */
class DefaultProjectDependencyConstraint(@JvmField val projectDependency: ProjectDependency) : AbstractDependencyConstraint() {
    private var reason: String? = null
    private var force = false

    override fun version(configureAction: Action<in MutableVersionConstraint>) {
        throw UnsupportedOperationException("Cannot change version constraint on a project dependency")
    }

    override fun getReason(): String? {
        return reason
    }

    override fun because(reason: String?) {
        validateMutation()
        this.reason = reason
    }

    override fun getAttributes(): AttributeContainer {
        return projectDependency.getAttributes()
    }

    override fun attributes(configureAction: Action<in AttributeContainer>): DependencyConstraint {
        validateMutation()
        projectDependency.attributes(configureAction)
        return this
    }

    override fun getVersionConstraint(): VersionConstraint {
        return DefaultImmutableVersionConstraint(
            "",
            projectDependency.getVersion()!!,
            "",
            mutableListOf<String>(),
            ""
        )
    }

    override fun getGroup(): String {
        return projectDependency.getGroup()!!
    }

    override fun getName(): String {
        return projectDependency.getName()
    }

    override fun getVersion(): String? {
        return projectDependency.getVersion()
    }

    override fun matchesStrictly(identifier: ModuleVersionIdentifier): Boolean {
        return identifier.getModule() == getModule() && identifier.getVersion() == projectDependency.getVersion()
    }

    override fun getModule(): ModuleIdentifier {
        val group = projectDependency.getGroup()
        return DefaultModuleIdentifier.newId(if (group != null) group else "", projectDependency.getName())
    }

    override fun setForce(force: Boolean) {
        validateMutation()
        this.force = force
    }

    override fun isForce(): Boolean {
        return force
    }

    override fun copy(): DependencyConstraint {
        val result = DefaultProjectDependencyConstraint(projectDependency.copy())
        result.force = force
        result.reason = reason
        return result
    }
}
