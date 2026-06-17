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
package org.gradle.api.internal.artifacts

import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.DomainObjectSet
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.DelegatingDomainObjectSet
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Actions

class DefaultDependencySet(private val displayName: Describable, private val clientConfiguration: ConfigurationInternal, backingSet: DomainObjectSet<Dependency?>) :
    DelegatingDomainObjectSet<Dependency?>(backingSet), DependencySet {
    private val mutationValidator: Action<in ModuleDependency?>

    init {
        this.mutationValidator = toMutationValidator(clientConfiguration)
    }

    protected fun toMutationValidator(clientConfiguration: Configuration?): Action<ModuleDependency?> {
        return if (clientConfiguration is MutationValidator) MutationValidationAction(clientConfiguration) else Actions.doNothing<ModuleDependency?>()
    }

    override fun toString(): String {
        return displayName.getDisplayName()
    }

    override fun getBuildDependencies(): TaskDependency {
        return clientConfiguration.getBuildDependencies()
    }

    override fun add(o: Dependency): Boolean {
        assertConfigurationIsDeclarable()
        clientConfiguration.maybeEmitDeclarationDeprecation()
        if (o is AbstractModuleDependency) {
            o.addMutationValidator(mutationValidator)
        }
        return super.add(o)
    }

    private fun assertConfigurationIsDeclarable() {
        if (!clientConfiguration.isCanBeDeclared()) {
            throw InvalidUserCodeException("Dependencies can not be declared against the `" + clientConfiguration.getName() + "` configuration.")
        }
    }

    override fun addAll(dependencies: MutableCollection<out Dependency>): Boolean {
        var added = false
        for (dependency in dependencies) {
            added = added or add(dependency)
        }
        return added
    }

    private class MutationValidationAction(private val clientConfiguration: Configuration) : Action<ModuleDependency?> {
        override fun execute(moduleDependency: ModuleDependency?) {
            (clientConfiguration as MutationValidator).validateMutation(MutationValidator.MutationType.DEPENDENCY_ATTRIBUTES)
        }
    }
}
