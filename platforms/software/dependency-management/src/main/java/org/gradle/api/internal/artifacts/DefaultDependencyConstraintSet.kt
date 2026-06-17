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
package org.gradle.api.internal.artifacts

import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.internal.DelegatingDomainObjectSet
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal

class DefaultDependencyConstraintSet(private val displayName: Describable, private val clientConfiguration: ConfigurationInternal, backingSet: DomainObjectSet<DependencyConstraint?>) :
    DelegatingDomainObjectSet<DependencyConstraint?>(backingSet), DependencyConstraintSet {
    override fun toString(): String {
        return displayName.getDisplayName()
    }

    override fun add(dependencyConstraint: DependencyConstraint): Boolean {
        assertConfigurationIsDeclarable()
        clientConfiguration.maybeEmitDeclarationDeprecation()
        if (dependencyConstraint is DependencyConstraintInternal) {
            dependencyConstraint.addMutationValidator(Action { constraint: DependencyConstraint? -> (clientConfiguration as MutationValidator).validateMutation(MutationValidator.MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES) }
            )
        }
        return addInternalDependencyConstraint(dependencyConstraint)
    }

    // For internal use only, allows adding a dependency constraint without issuing a deprecation warning
    fun addInternalDependencyConstraint(dependencyConstraint: DependencyConstraint): Boolean {
        return super.add(dependencyConstraint)
    }

    private fun assertConfigurationIsDeclarable() {
        if (!clientConfiguration.isCanBeDeclared()) {
            throw GradleException("Dependency constraints can not be declared against the `" + clientConfiguration.getName() + "` configuration.")
        }
    }

    override fun addAll(dependencyConstraints: MutableCollection<out DependencyConstraint>): Boolean {
        var added = false
        for (dependencyConstraint in dependencyConstraints) {
            added = added or add(dependencyConstraint)
        }
        return added
    }
}
